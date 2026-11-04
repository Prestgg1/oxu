# SPDX-License-Identifier: GPL-3.0-or-later
# Native offline translation engine: Mozilla's bergamot-translator (Marian NMT)
# plus CLD2 for source language detection. Build recipe adapted from
# https://github.com/DavidVentura/offline-translator (GPL-3.0).
cmake_minimum_required(VERSION 3.22.1)
project("oxu-bergamot")

add_compile_options("-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)

set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
set(USE_WASM_COMPATIBLE_SOURCES OFF CACHE BOOL "" FORCE)
set(COMPILE_WASM OFF CACHE BOOL "" FORCE)
set(COMPILE_TEST OFF CACHE BOOL "" FORCE)
set(USE_THREADS OFF CACHE BOOL "" FORCE)
set(INTGEMM_DONT_BUILD_TESTS ON)
set(USE_DOXYGEN OFF CACHE BOOL "" FORCE)
set(COMPILE_WITHOUT_EXCEPTIONS ON)
set(USE_RUY ON CACHE BOOL "" FORCE)
set(USE_RUY_SGEMM ON CACHE BOOL "" FORCE)
set(SSPLIT_COMPILE_LIBRARY_ONLY ON)
set(SSPLIT_USE_INTERNAL_PCRE2 ON CACHE BOOL "" FORCE)
set(AUTO_CPU_DETECT OFF CACHE BOOL "" FORCE)
set(COMPILE_LIBRARY_ONLY ON CACHE BOOL "" FORCE)
set(COMPILE_UNIT_TESTS OFF CACHE BOOL "" FORCE)
set(ENABLE_CACHE_STATS OFF CACHE BOOL "" FORCE)
set(USE_MKL OFF CACHE BOOL "" FORCE)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(BUILD_ARCH "armv8-a" CACHE STRING "" FORCE)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(BUILD_ARCH "armv7-a" CACHE STRING "" FORCE)
endif()

# Small, deterministic output (F-Droid builds are reproducible).
set(OXU_FLAGS "-s -fdebug-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=. -Wno-builtin-macro-redefined -ffunction-sections -fdata-sections -fvisibility=hidden")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${OXU_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OXU_FLAGS} -fvisibility-inlines-hidden")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--build-id=none -Wl,--hash-style=gnu -Wl,--gc-sections -Wl,--strip-all -Wl,--exclude-libs,ALL -Wl,--sort-section=name -Wl,--sort-common -Wl,-z,relro -Wl,-z,now -Wl,-z,max-page-size=16384")

add_subdirectory(bergamot-translator)
include(${CMAKE_CURRENT_SOURCE_DIR}/cmake/cld2.cmake)

add_library(oxu-bergamot SHARED oxu_bergamot.cpp)
target_link_libraries(oxu-bergamot PRIVATE bergamot-translator cld2 log)
# CMake 3.22 adds -fuse-ld=gold when IPO is on for the linking target; gold can't
# link Android objects. lld runs ThinLTO on the bitcode archives by itself.
set_property(TARGET oxu-bergamot PROPERTY INTERPROCEDURAL_OPTIMIZATION FALSE)
target_link_options(oxu-bergamot PRIVATE -fuse-ld=lld -flto=thin)
