
cmake_minimum_required(VERSION 3.22.1)
project(edith_jni LANGUAGES CXX)

# Use Android's shared C++ runtime
set(CMAKE_ANDROID_STL_TYPE c++_shared)

# Paths
set(NATIVE_LIBS "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/arm64-v8a")
set(LLAMA_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp")

# llama.cpp dependencies
add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES
    IMPORTED_LOCATION "${NATIVE_LIBS}/libggml-base.so"
)

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES
    IMPORTED_LOCATION "${NATIVE_LIBS}/libggml.so"
)

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES
    IMPORTED_LOCATION "${NATIVE_LIBS}/libggml-cpu.so"
)

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES
    IMPORTED_LOCATION "${NATIVE_LIBS}/libllama.so"
)

# EDITH JNI bridge
add_library(edith_jni SHARED native-lib.cpp)

target_include_directories(edith_jni PRIVATE
    "${LLAMA_ROOT}/include"
    "${LLAMA_ROOT}/ggml/include"
)

# Android logging
find_library(android-log log)

# Link native libraries and C++ runtime
target_link_libraries(edith_jni PRIVATE
    llama
    ggml-cpu
    ggml
    ggml-base
    ${android-log}
    c++_shared
)
