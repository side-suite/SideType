cmake_minimum_required(VERSION 3.22.1)
project(sidetype_whisper C CXX)

# whisper.cpp (MIT) is fetched at configure time rather than vendored: a release tarball pinned by
# its SHA-256, so a changed download fails the build instead of silently compiling something else.
# To bump it, change both the tag and the hash.
include(FetchContent)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)        # link whisper + ggml statically into our one .so
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)              # cross-compiling: never tune for the build host
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)              # plain threads; avoids shipping libomp.so
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
	whisper
	URL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.9.5.tar.gz
	URL_HASH SHA256=ff1a9053feb509ff9d7729703355541ae9690073a6b1c40eb692c962e0dc1720
)
FetchContent_MakeAvailable(whisper)

add_library(sidetype_whisper SHARED whisper_jni.c)
target_link_libraries(sidetype_whisper PRIVATE whisper log)
target_link_options(sidetype_whisper PRIVATE "-Wl,-z,max-page-size=16384")
