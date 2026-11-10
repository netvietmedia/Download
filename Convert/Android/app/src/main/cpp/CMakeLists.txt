cmake_minimum_required(VERSION 3.22.1)

project("shareit_native_core")

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

add_library(
    shareit_native_core
    SHARED
    native-core.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    shareit_native_core
    ${log-lib}
)

target_link_options(
    shareit_native_core PRIVATE
    "-Wl,-z,max-page-size=16384"
)
