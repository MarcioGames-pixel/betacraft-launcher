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
    target_link_libraries(GL PRIVATE dl log)
endif()
