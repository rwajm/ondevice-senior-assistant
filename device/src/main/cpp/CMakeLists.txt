cmake_minimum_required(VERSION 3.22.1)
project(genie_jni C)

# Plain C and no link against libGenie.so: it is opened at run time (see genie_jni.c)
add_library(genie_jni SHARED genie_jni.c)
target_compile_options(genie_jni PRIVATE -Wall -Wextra -Werror)
target_link_libraries(genie_jni log dl)
