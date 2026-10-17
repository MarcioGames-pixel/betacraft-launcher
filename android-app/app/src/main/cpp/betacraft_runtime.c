#include <jni.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <limits.h>
#include <android/log.h>

#define LOG_TAG "BetaCraftRuntime"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

typedef jint (*JLI_Launch_func)(int, char **, int, const char **, int,
        const char **, const char *, const char *, const char *, const char *,
        jboolean, jboolean, jboolean, jint);

static void set_runtime_environment(const char *java_home, const char *jli_path) {
    setenv("JAVA_HOME", java_home, 1);
    char root[PATH_MAX];
    if (strlen(jli_path) < sizeof(root)) {
        strcpy(root, jli_path);
        char *last = strrchr(root, '/');
        if (last) {
            *last = '\0';
            char library_path[PATH_MAX * 2];
            snprintf(library_path, sizeof(library_path),
                     "%s:%s/lib:%s/lib/server:%s/lib/aarch64:%s/lib/aarch64/server",
                     root, java_home, java_home, java_home, java_home);
            setenv("LD_LIBRARY_PATH", library_path, 1);
        }
    }
}

JNIEXPORT jint JNICALL
Java_com_marciogames_betacraft_android_AndroidJvmBridge_runNative(
        JNIEnv *env, jclass clazz, jstring java_home_string,
        jstring jli_path_string, jobjectArray args_array) {
    (void) clazz;
    if (!java_home_string || !jli_path_string || !args_array) {
        LOGE("JVM launch received null arguments");
        return -2;
    }

    const char *java_home = (*env)->GetStringUTFChars(env, java_home_string, NULL);
    const char *jli_path = (*env)->GetStringUTFChars(env, jli_path_string, NULL);
    if (!java_home || !jli_path) {
        if (java_home) (*env)->ReleaseStringUTFChars(env, java_home_string, java_home);
        if (jli_path) (*env)->ReleaseStringUTFChars(env, jli_path_string, jli_path);
        return -3;
    }

    set_runtime_environment(java_home, jli_path);
    void *handle = dlopen(jli_path, RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        LOGE("Could not load libjli.so: %s", dlerror());
        (*env)->ReleaseStringUTFChars(env, java_home_string, java_home);
        (*env)->ReleaseStringUTFChars(env, jli_path_string, jli_path);
        return -4;
    }

    dlerror();
    JLI_Launch_func launch = (JLI_Launch_func) dlsym(handle, "JLI_Launch");
    const char *symbol_error = dlerror();
    if (!launch || symbol_error) {
        LOGE("JLI_Launch symbol unavailable: %s", symbol_error ? symbol_error : "unknown error");
        (*env)->ReleaseStringUTFChars(env, java_home_string, java_home);
        (*env)->ReleaseStringUTFChars(env, jli_path_string, jli_path);
        return -5;
    }

    jsize argc = (*env)->GetArrayLength(env, args_array);
    if (argc < 1 || argc > 4096) {
        LOGE("Invalid JVM argument count: %d", (int) argc);
        (*env)->ReleaseStringUTFChars(env, java_home_string, java_home);
        (*env)->ReleaseStringUTFChars(env, jli_path_string, jli_path);
        return -6;
    }

    char **argv = calloc((size_t) argc + 1, sizeof(char *));
    if (!argv) {
        (*env)->ReleaseStringUTFChars(env, java_home_string, java_home);
        (*env)->ReleaseStringUTFChars(env, jli_path_string, jli_path);
        return -7;
    }

    jint result = -8;
    int converted = 0;
    for (jsize i = 0; i < argc; i++) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, args_array, i);
        if (!item) break;
        const char *utf = (*env)->GetStringUTFChars(env, item, NULL);
        if (!utf) {
            (*env)->DeleteLocalRef(env, item);
            break;
        }
        argv[i] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, item, utf);
        (*env)->DeleteLocalRef(env, item);
        if (!argv[i]) break;
        converted++;
    }

    if (converted == argc) {
        LOGI("Calling OpenJDK JLI_Launch with %d arguments", (int) argc);
        const char *full_version = "1.8.0-internal";
        const char *dot_version = "1.8";
        result = launch((int) argc, argv, 0, NULL, 0, NULL,
                        full_version, dot_version, "java", "openjdk",
                        JNI_FALSE, JNI_TRUE, JNI_FALSE, 0);
        LOGI("JLI_Launch returned %d", (int) result);
    } else {
        LOGE("Could not convert all JVM arguments");
    }

    for (jsize i = 0; i < argc; i++) free(argv[i]);
    free(argv);
    (*env)->ReleaseStringUTFChars(env, java_home_string, java_home);
    (*env)->ReleaseStringUTFChars(env, jli_path_string, jli_path);
    return result;
}
