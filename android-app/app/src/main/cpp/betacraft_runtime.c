#include <jni.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <limits.h>
#include <dirent.h>
#include <sys/stat.h>
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


/* Android's dynamic linker does not reliably re-read LD_LIBRARY_PATH after
 * process startup. Locate and preload runtime libraries by absolute path,
 * as Pojav's launcher does, before JLI tries to resolve them by soname. */
static int find_library_path(const char *directory, const char *name,
        char *result, size_t result_size, int depth) {
    if (!directory || depth > 10) return 0;
    DIR *dir = opendir(directory);
    if (!dir) return 0;
    struct dirent *entry;
    int found = 0;
    while (!found && (entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
        char child[PATH_MAX];
        int written = snprintf(child, sizeof(child), "%s/%s", directory, entry->d_name);
        if (written < 0 || (size_t) written >= sizeof(child)) continue;
        struct stat st;
        if (lstat(child, &st) != 0) continue;
        if (S_ISREG(st.st_mode) && strcmp(entry->d_name, name) == 0) {
            if (strlen(child) + 1 <= result_size) {
                strcpy(result, child);
                found = 1;
            }
        } else if (S_ISDIR(st.st_mode) && !S_ISLNK(st.st_mode)) {
            found = find_library_path(child, name, result, result_size, depth + 1);
        }
    }
    closedir(dir);
    return found;
}

static int preload_runtime_library(const char *java_home, const char *name, int required) {
    char path[PATH_MAX];
    if (!find_library_path(java_home, name, path, sizeof(path), 0)) {
        if (required) LOGE("Required runtime library not found: %s", name);
        else LOGI("Optional runtime library not found: %s", name);
        return required ? 0 : 1;
    }
    void *handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        LOGE("Could not preload %s: %s", path, dlerror());
        return required ? 0 : 1;
    }
    LOGI("Preloaded runtime library: %s", path);
    return 1;
}

static int preload_jvm_dependencies(const char *java_home) {
    /* libjvm must be loaded before JLI_Launch so Android can resolve it even
       when the runtime's native directories are outside the app's linker paths. */
    if (!preload_runtime_library(java_home, "libjvm.so", 1)) return 0;
    const char *optional_libraries[] = {
        "libverify.so", "libjava.so", "libnet.so", "libnio.so",
        "libawt.so", "libawt_headless.so", "libfreetype.so", "libfontmanager.so",
        "libmanagement.so", "libinstrument.so", "libzip.so", "libjdwp.so"
    };
    for (size_t i = 0; i < sizeof(optional_libraries) / sizeof(optional_libraries[0]); i++) {
        preload_runtime_library(java_home, optional_libraries[i], 0);
    }
    return 1;
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
    if (!preload_jvm_dependencies(java_home)) {
        dlclose(handle);
        (*env)->ReleaseStringUTFChars(env, java_home_string, java_home);
        (*env)->ReleaseStringUTFChars(env, jli_path_string, jli_path);
        return -9;
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
        int java_major = strstr(java_home, "java17") != NULL ? 17 : 8;
        const char *full_version = java_major == 17 ? "17.0.0-internal" : "1.8.0-internal";
        const char *dot_version = java_major == 17 ? "17" : "1.8";
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
