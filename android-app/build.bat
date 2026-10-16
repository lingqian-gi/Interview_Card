@echo off
REM 一键编译脚本（使用 Android Studio 自带 JDK 21 + 本机 SDK）
set JAVA_TOOL_OPTIONS=
set JAVA_HOME=D:\Android_Studio\jbr
set ANDROID_HOME=D:\Android_SDK
set ANDROID_SDK_ROOT=D:\Android_SDK
cd /d "%~dp0"
call gradlew.bat %*
