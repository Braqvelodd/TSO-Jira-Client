@echo off
setlocal enabledelayedexpansion

:: ==========================================
:: CONFIGURATION: Set to HOME or WORK
:: ==========================================
set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

set ENV=WORK
set EMBED_MODEL=NO

if "%ENV%"=="WORK" (
    set "JDK_BIN=C:\Program Files\Java\jdk21\TSO\bin"
    set "SOURCES=@sources.txt"
) else (
    set "JDK_BIN=C:\Program Files\Java\jdk-25\bin"
    set "SOURCES=@sources.txt"
)

echo Running in %ENV% mode (Embed Model: %EMBED_MODEL%)...
cd /d "%ROOT_DIR%"

:: Ensure bin directory exists
if not exist bin mkdir bin

:: Reassemble the model file from chunks (Only if EMBED_MODEL is YES)
if "%EMBED_MODEL%"=="YES" (
    if not exist embedding\models\model.gguf (
        echo Reassembling model.gguf from chunks for production build...
        if not exist embedding\models mkdir embedding\models
        copy /b lib\model.gguf.part* embedding\models\model.gguf >nul
    ) else (
        echo AI model already assembled, skipping...
    )
    set "JAR_RESOURCES=-C bin . -C resources . -C embedding ."
) else (
    echo Skipping model assembly for fast build...
    set "JAR_RESOURCES=-C bin . -C resources . -C embedding\bin ."
)

:: Setting up embedding bin
if not exist embedding\bin mkdir embedding\bin
copy lib\llama-cli.exe embedding\bin\llama-cli.exe >nul
copy lib\*.dll embedding\bin\ >nul
echo Embedding binaries and DLLs set up.

:: Unzipping library
echo Unzipping library...
cd bin
"%JDK_BIN%\jar" -xf ..\lib\json-20231013.jar
cd ..

:: Compile the Java files
echo Compiling Java files...
"%JDK_BIN%\javac" --release 8 -d bin -cp bin %SOURCES%

if %errorlevel% neq 0 (
    echo Java compilation failed!
    pause
    exit /b %errorlevel%
)

:: Create the JAR file
echo Creating JAR file...
"%JDK_BIN%\jar" cvfe JiraApiClient.jar tso.usmc.jira.app.JiraApiClientGui %JAR_RESOURCES%

if %errorlevel% neq 0 (
    echo JAR creation failed!
    exit /b %errorlevel%
)

echo Process completed successfully for %ENV% environment!
pause

if %errorlevel% neq 0 (
    echo JAR creation failed!
    exit /b %errorlevel%
)

echo Process completed successfully for %ENV% environment!
pause
