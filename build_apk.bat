@echo off
setlocal enabledelayedexpansion
title MoodBeats Android APK Builder

echo ========================================================
echo        MoodBeats Android APK Build System
echo ========================================================
echo.

where python >nul 2>nul
if %errorlevel% equ 0 (
    echo [*] Running automated Python build engine...
    python %~dp0build_native_apk.py
    if %errorlevel% equ 0 (
        echo.
        echo [OK] Build completed successfully via Python.
        pause
        exit /b 0
    )
)

set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

set SDK_ROOT=C:\Users\admin\AppData\Local\Android\Sdk
set BUILD_TOOLS=%SDK_ROOT%\build-tools\34.0.0
set PLATFORM=%SDK_ROOT%\platforms\android-34\android.jar
set PROJECT_DIR=%~dp0
if %PROJECT_DIR:~-1%==" set PROJECT_DIR=%PROJECT_DIR:~0,-1%

set AAPT2=%BUILD_TOOLS%\aapt2.exe
set D8=%BUILD_TOOLS%\d8.bat
set ZIPALIGN=%BUILD_TOOLS%\zipalign.exe
set APKSIGNER=%BUILD_TOOLS%\apksigner.bat
set JAVAC=%JAVA_HOME%\bin\javac.exe
set JAR=%JAVA_HOME%\bin\jar.exe

echo [*] Cleaning intermediate build artifacts...
if exist %PROJECT_DIR%\build rd /s /q %PROJECT_DIR%\build
if exist %PROJECT_DIR%\gen rd /s /q %PROJECT_DIR%\gen
if exist %PROJECT_DIR%\bin rd /s /q %PROJECT_DIR%\bin
if exist %PROJECT_DIR%\dex rd /s /q %PROJECT_DIR%\dex

mkdir %PROJECT_DIR%\build
mkdir %PROJECT_DIR%\gen
mkdir %PROJECT_DIR%\bin
mkdir %PROJECT_DIR%\dex

echo [*] Step 1/6: Compiling Android resources with AAPT2...
%AAPT2% compile --dir %PROJECT_DIR%\app\src\main\res -o %PROJECT_DIR%\build\compiled_res.zip
if %errorlevel% neq 0 (
 echo [!] Error compiling resources.
 pause
 exit /b %errorlevel%
)

echo [*] Step 2/6: Linking package and generating R.java...
%AAPT2% link %PROJECT_DIR%\build\compiled_res.zip ^
 -I %PLATFORM% ^
 --manifest %PROJECT_DIR%\app\src\main\AndroidManifest.xml ^
 --java %PROJECT_DIR%\gen ^
 -A %PROJECT_DIR%\app\src\main\assets ^
 -o %PROJECT_DIR%\build\base.apk ^
 --auto-add-overlay
if %errorlevel% neq 0 (
 echo [!] Error linking resources.
 pause
 exit /b %errorlevel%
)

echo [*] Step 3/6: Compiling Java sources with javac...
%JAVAC% -cp %PLATFORM% -d %PROJECT_DIR%\bin -source 1.8 -target 1.8 ^
 %PROJECT_DIR%\gen\com\aurabeats\moodbeats\R.java ^
 %PROJECT_DIR%\app\src\main\java\com\aurabeats\moodbeats\*.java
if %errorlevel% neq 0 (
 echo [!] Error compiling Java sources.
 pause
 exit /b %errorlevel%
)

echo [*] Step 4/6: Converting bytecode with D8 (dex)...
powershell -NoProfile -Command  = Get-ChildItem -Recurse '%PROJECT_DIR%\bin' -Filter '*.class' | Select-Object -ExpandProperty FullName; & '%D8%' --lib '%PLATFORM%' --output '%PROJECT_DIR%\dex' 
if %errorlevel% neq 0 (
 echo [!] Error generating dex.
 pause
 exit /b %errorlevel%
)

echo [*] Step 5/6: Packaging and aligning APK...
%JAR% uf %PROJECT_DIR%\build\base.apk -C %PROJECT_DIR%\dex classes.dex
%ZIPALIGN% -f -p 4 %PROJECT_DIR%\build\base.apk %PROJECT_DIR%\build\aligned.apk
if %errorlevel% neq 0 (
 echo [!] Error aligning APK.
 pause
 exit /b %errorlevel%
)

echo [*] Step 6/6: Signing APK with apksigner...
call %APKSIGNER% sign --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --ks %PROJECT_DIR%\debug.keystore --ks-pass pass:android --key-pass pass:android --out %PROJECT_DIR%\MoodBeats.apk %PROJECT_DIR%\build\aligned.apk
if %errorlevel% neq 0 (
 echo [!] Error signing APK.
 pause
 exit /b %errorlevel%
)

copy /y %PROJECT_DIR%\MoodBeats.apk D:\MoodBeats\MoodBeats.apk >nul

echo.
echo ========================================================
echo SUCCESS! Android APK Built Successfully!
echo ========================================================
echo.
echo Primary APK location:
echo - %PROJECT_DIR%\MoodBeats.apk
echo.
echo MoodBeats root copy:
echo - D:\MoodBeats\MoodBeats.apk
echo.
echo ========================================================
pause
