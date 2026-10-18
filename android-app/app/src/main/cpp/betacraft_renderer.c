#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <pthread.h>
#include <stdatomic.h>
#include <time.h>
#include <math.h>

#define INPUT_QUEUE_CAPACITY 256
typedef struct { int type; int action; int code; float x; float y; } InputEvent;
static InputEvent input_queue[INPUT_QUEUE_CAPACITY];
static unsigned int input_read = 0;
static unsigned int input_write = 0;
static pthread_mutex_t input_mutex = PTHREAD_MUTEX_INITIALIZER;

#define TAG "BetaCraftRenderer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

typedef struct {
    ANativeWindow *window;
    pthread_t thread;
    atomic_int stopping;
    int started;
    atomic_int surface_width;
    atomic_int surface_height;
} RenderState;

static RenderState state;
static pthread_mutex_t state_mutex = PTHREAD_MUTEX_INITIALIZER;

static void *render_thread(void *opaque) {
    RenderState *s = (RenderState *) opaque;
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext context = EGL_NO_CONTEXT;
    if (display == EGL_NO_DISPLAY || !eglInitialize(display, NULL, NULL)) {
        LOGE("eglInitialize failed: 0x%x", eglGetError());
        goto done;
    }

    const EGLint config_attrs[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };
    EGLConfig config;
    EGLint count = 0;
    if (!eglChooseConfig(display, config_attrs, &config, 1, &count) || count < 1) {
        LOGE("No EGL window config available: 0x%x", eglGetError());
        goto done;
    }
    const EGLint context_attrs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attrs);
    if (context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed: 0x%x", eglGetError());
        goto done;
    }
    surface = eglCreateWindowSurface(display, config, s->window, NULL);
    if (surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed: 0x%x", eglGetError());
        goto done;
    }
    if (!eglMakeCurrent(display, surface, surface, context)) {
        LOGE("eglMakeCurrent failed: 0x%x", eglGetError());
        goto done;
    }

    EGLint width = 0, height = 0;
    eglQuerySurface(display, surface, EGL_WIDTH, &width);
    eglQuerySurface(display, surface, EGL_HEIGHT, &height);
    atomic_store(&s->surface_width, width);
    atomic_store(&s->surface_height, height);
    glViewport(0, 0, width, height);
    LOGI("Android EGL test surface ready (%d x %d); this is not Minecraft rendering yet", width, height);

    while (!atomic_load(&s->stopping)) {
        // A moving clear colour verifies that EGL can keep presenting frames.
        static float phase = 0.0f;
        phase += 0.012f;
        if (phase > 1.0f) phase = 0.0f;
        EGLint current_width = 0, current_height = 0;
        if (eglQuerySurface(display, surface, EGL_WIDTH, &current_width)
                && eglQuerySurface(display, surface, EGL_HEIGHT, &current_height)
                && current_width > 0 && current_height > 0) {
            width = current_width;
            height = current_height;
        }
        int requested_width = atomic_load(&s->surface_width);
        int requested_height = atomic_load(&s->surface_height);
        if (requested_width > 0 && requested_height > 0) {
            width = requested_width;
            height = requested_height;
        }
        glViewport(0, 0, width, height);
        glClearColor(0.055f + phase * 0.08f, 0.075f + phase * 0.04f, 0.11f + phase * 0.03f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        if (!eglSwapBuffers(display, surface)) {
            EGLint err = eglGetError();
            if (!atomic_load(&s->stopping)) LOGE("eglSwapBuffers failed: 0x%x", err);
            break;
        }
        struct timespec delay = {0, 33333333L};
        nanosleep(&delay, NULL);
    }

done:
    if (display != EGL_NO_DISPLAY) {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
        if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
        eglTerminate(display);
    }
    ANativeWindow_release(s->window);
    s->window = NULL;
    return NULL;
}

/* Bounded native event queue. The future LWJGL Android adapter must drain this queue
 * and translate Android key/action codes to LWJGL events; storing events alone does not
 * make Minecraft input work yet. Oldest events are dropped if the consumer falls behind. */
JNIEXPORT void JNICALL
Java_com_marciogames_betacraft_android_NativeGameSurface_nativeSendInput(
        JNIEnv *env, jclass clazz, jint type, jint action, jint code, jfloat x, jfloat y) {
    (void) env; (void) clazz;
    pthread_mutex_lock(&input_mutex);
    unsigned int next = (input_write + 1U) % INPUT_QUEUE_CAPACITY;
    if (next == input_read) input_read = (input_read + 1U) % INPUT_QUEUE_CAPACITY;
    input_queue[input_write].type = type;
    input_queue[input_write].action = action;
    input_queue[input_write].code = code;
    input_queue[input_write].x = isfinite(x) ? x : 0.0f;
    input_queue[input_write].y = isfinite(y) ? y : 0.0f;
    input_write = next;
    pthread_mutex_unlock(&input_mutex);
}

JNIEXPORT jfloatArray JNICALL
Java_com_marciogames_betacraft_android_NativeGameSurface_nativePollInput(
        JNIEnv *env, jclass clazz) {
    (void) clazz;
    InputEvent event;
    pthread_mutex_lock(&input_mutex);
    if (input_read == input_write) {
        pthread_mutex_unlock(&input_mutex);
        return NULL;
    }
    event = input_queue[input_read];
    input_read = (input_read + 1U) % INPUT_QUEUE_CAPACITY;
    pthread_mutex_unlock(&input_mutex);
    jfloat values[5] = {(jfloat)event.type, (jfloat)event.action,
                        (jfloat)event.code, event.x, event.y};
    jfloatArray result = (*env)->NewFloatArray(env, 5);
    if (result) (*env)->SetFloatArrayRegion(env, result, 0, 5, values);
    return result;
}

JNIEXPORT void JNICALL
Java_com_marciogames_betacraft_android_NativeGameSurface_nativeResize(
        JNIEnv *env, jclass clazz, jint width, jint height) {
    (void) env; (void) clazz;
    if (width <= 0 || height <= 0) return;
    atomic_store(&state.surface_width, width);
    atomic_store(&state.surface_height, height);
}

JNIEXPORT jboolean JNICALL
Java_com_marciogames_betacraft_android_NativeGameSurface_nativeStart(
        JNIEnv *env, jclass clazz, jobject java_surface) {
    (void) clazz;
    pthread_mutex_lock(&state_mutex);
    if (state.started) {
        atomic_store(&state.stopping, 1);
        pthread_join(state.thread, NULL);
        state.started = 0;
    }
    state.window = ANativeWindow_fromSurface(env, java_surface);
    if (!state.window) {
        pthread_mutex_unlock(&state_mutex);
        LOGE("ANativeWindow_fromSurface failed");
        return JNI_FALSE;
    }
    atomic_store(&state.stopping, 0);
    state.started = 1;
    int result = pthread_create(&state.thread, NULL, render_thread, &state);
    if (result != 0) {
        state.started = 0;
        ANativeWindow_release(state.window);
        state.window = NULL;
        pthread_mutex_unlock(&state_mutex);
        LOGE("pthread_create failed: %d", result);
        return JNI_FALSE;
    }
    pthread_mutex_unlock(&state_mutex);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_marciogames_betacraft_android_NativeGameSurface_nativeStop(
        JNIEnv *env, jclass clazz) {
    (void) env; (void) clazz;
    pthread_mutex_lock(&state_mutex);
    if (state.started) {
        atomic_store(&state.stopping, 1);
        pthread_join(state.thread, NULL);
        state.started = 0;
    }
    pthread_mutex_unlock(&state_mutex);
}
