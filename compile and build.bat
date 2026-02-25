@echo off
setlocal enabledelayedexpansion

:: ==========================================
:: CONFIGURATION: Set to HOME or WORK
:: ==========================================
set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

set ENV=HOME

if "%ENV%"=="WORK" (
    set "JDK_BIN=C:\Program Files\Java\jdk21\TSO\bin"
    set "SOURCES=@sources.txt"
) else (
    set "JDK_BIN=C:\Program Files\Java\jdk-25\bin"
    set "SOURCES=@sources.txt"
)

echo Running in %ENV% mode...
cd /d "%ROOT_DIR%"

:: Ensure bin directory exists
if not exist bin mkdir bin

:: Reassemble the model file from chunks
if not exist embedding\models\llama-3-8b.gguf (
    echo Reassembling llama-3-8b.gguf from chunks...
    if not exist embedding\models mkdir embedding\models
    copy /b lib\llama-3-8b.gguf.part* embedding\models\llama-3-8b.gguf >nul
) else (
    echo AI model already assembled, skipping...
)

:: Setting up embedding bin
if not exist embedding\bin\llama-cli.exe (
    echo Setting up embedding bin...
    if not exist embedding\bin mkdir embedding\bin
    copy lib\llama-cli.exe embedding\bin\llama-cli.exe >nul
) else (
    echo Embedding binary already set up, skipping...
)

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
"%JDK_BIN%\jar" cvfe JiraApiClient.jar tso.usmc.jira.app.JiraApiClientGui -C bin . -C resources . -C embedding .

if %errorlevel% neq 0 (
    echo JAR creation failed!
    exit /b %errorlevel%
)

echo Process completed successfully for %ENV% environment!
pause
