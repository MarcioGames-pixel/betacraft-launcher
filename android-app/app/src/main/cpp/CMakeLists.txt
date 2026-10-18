cmake_minimum_required(VERSION 3.22.1)
project(betacraft_runtime C)

add_library(betacraft_runtime SHARED betacraft_runtime.c)
target_compile_features(betacraft_runtime PRIVATE c_std_11)
target_compile_options(betacraft_runtime PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(betacraft_runtime PRIVATE dl log)

# GL4ES translates legacy desktop OpenGL calls to OpenGL ES. It is a required
# building block for older Minecraft/LWJGL versions, but it does not create an
# EGL surface or make Minecraft launch by itself.
include(FetchContent)
set(ANDROID ON CACHE BOOL "Build GL4ES for Android" FORCE)
set(NOX11 ON CACHE BOOL "No X11 on Android" FORCE)
set(GBM OFF CACHE BOOL "No GBM on Android" FORCE)
set(USE_ANDROID_LOG ON CACHE BOOL "Use Android log" FORCE)
set(EGL_WRAPPER OFF CACHE BOOL "Use Android's EGL implementation" FORCE)
set(GLX_STUBS OFF CACHE BOOL "No GLX stubs needed yet" FORCE)

FetchContent_Declare(
    gl4es
    GIT_REPOSITORY https://github.com/PojavLauncherTeam/gl4es.git
    GIT_TAG 3d8906d46a93066f21ea01aeaf1a36ec972efd15
    GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(gl4es)

if(TARGET GL)
    # The legacy JVM/LWJGL loaders search for libGL.so. Keep this target in
    # Android's native-library packaging rather than writing into source dirs.
    set_target_properties(GL PROPERTIES
        OUTPUT_NAME "GL"
        PREFIX "lib"
        SUFFIX ".so"
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
    )
endif()

# PojavLauncher-derived Android GLFW backend.
# The stock desktop GLFW backend cannot bind an Android SurfaceView. This pinned
# MojoLauncher fork includes GLFW_BUILD_ANDROID support and is the native window
# backend used by Pojav's SurfaceProvider/GLFWBackend architecture. Building it is
# only one part of the port: BetaCraft still needs the matching Java/JNI bridge and
# LWJGL2-to-LWJGL3 compatibility JAR before Minecraft can render through it.
include(FetchContent)
set(GLFW_BUILD_ANDROID ON CACHE BOOL "Build Android GLFW platform" FORCE)
set(GLFW_BUILD_X11 OFF CACHE BOOL "No X11 on Android" FORCE)
set(GLFW_BUILD_WAYLAND OFF CACHE BOOL "No Wayland on Android" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "No GLFW examples in APK" FORCE)
set(GLFW_BUILD_TESTS OFF CACHE BOOL "No GLFW tests in APK" FORCE)
set(GLFW_BUILD_DOCS OFF CACHE BOOL "No GLFW docs in APK" FORCE)
set(GLFW_INSTALL OFF CACHE BOOL "No GLFW install step" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Package GLFW as shared library" FORCE)

FetchContent_Declare(
    pojav_glfw
    GIT_REPOSITORY https://github.com/MojoLauncher/glfw.git
    GIT_TAG 7900e19fd2dbcad5abb4affc3b9d56c27e164c59
    GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(pojav_glfw)

if(TARGET glfw)
    target_compile_options(glfw PRIVATE -Wno-nullability-completeness)
endif()

# Native EGL/GLES2 surface smoke test used to validate Android window lifecycle.
add_library(betacraft_renderer SHARED betacraft_renderer.c)
target_compile_features(betacraft_renderer PRIVATE c_std_11)
target_compile_options(betacraft_renderer PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(betacraft_renderer PRIVATE android EGL GLESv2 log)
