cmake_minimum_required(VERSION 3.22.1)
project(oberjein LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(oberjein SHARED native-lib.cpp)
target_compile_options(oberjein PRIVATE -O3 -Wall)
target_link_libraries(oberjein oboe::oboe log android)
