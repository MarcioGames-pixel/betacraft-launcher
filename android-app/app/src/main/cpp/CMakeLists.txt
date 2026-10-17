cmake_minimum_required(VERSION 3.22.1)
project(betacraft_runtime C)

add_library(betacraft_runtime SHARED betacraft_runtime.c)
target_compile_features(betacraft_runtime PRIVATE c_std_11)
target_compile_options(betacraft_runtime PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(betacraft_runtime PRIVATE dl log)
