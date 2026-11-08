cmake_minimum_required(VERSION 3.22.1)
project("amar_core" C)

set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/app/src/main/cpp
)

add_definitions(
    -D_FILE_OFFSET_BITS=64
)

add_library(
    amar_core
    SHARED
    app/src/main/cpp/amar_core.c
)

find_library(log-lib log)

target_link_libraries(
    amar_core
    ${log-lib}
    m
)

target_compile_options(
    amar_core
    PRIVATE
    -std=c17
    -Wall
    -Wextra
    -Wno-unused-parameter
    -O3
)

