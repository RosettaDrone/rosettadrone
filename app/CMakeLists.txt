# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

include_directories(${CMAKE_SOURCE_DIR}/src/main/cpp/ ${CMAKE_SOURCE_DIR}/src/main/cpp/include
                        ${CMAKE_SOURCE_DIR}/src/main/cpp/gstreamer-1.0/armv7/include/gstreamer-1.0
                        ${CMAKE_SOURCE_DIR}/src/main/cpp/gstreamer-1.0/armv7/include/glib-2.0
                        ${CMAKE_SOURCE_DIR}/src/main/cpp/gstreamer-1.0/armv7/lib/glib-2.0/include )

set(gst_DIR ${CMAKE_SOURCE_DIR}/src/main/cpp/lib)

add_library(gstreamer_android SHARED IMPORTED)
set_target_properties(gstreamer_android PROPERTIES IMPORTED_LOCATION
         ${gst_DIR}/${ANDROID_ABI}/libgstreamer_android.so)


set(ffmpeg_DIR ${CMAKE_SOURCE_DIR}/src/main/cpp/lib)

add_library(ffmpegjni SHARED IMPORTED)
set_target_properties(ffmpegjni PROPERTIES IMPORTED_LOCATION
         ${ffmpeg_DIR}/${ANDROID_ABI}/libffmpeg.so)


add_library( # Specifies the name of the library.
             djivideojni

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/dji_video_jni.c )

find_library( log-lib log )

target_link_libraries( djivideojni ffmpegjni ${log-lib} )

add_library( # Specifies the name of the library.
             VideoEncoderJNI

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/video_encoder.c )

target_link_libraries( VideoEncoderJNI gstreamer_android ${log-lib} )
