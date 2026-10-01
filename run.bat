@echo off
setlocal enabledelayedexpansion

:: Ensure script runs from its own directory
cd /d "%~dp0"

:: ==============================================================================
:: Locate Java 8 (JRE/JDK 1.8) Runtime
:: JiraApiClient requires Java 8 for embedded JavaFX desktop support.
:: ==============================================================================
set "JAVA_EXE="

:: 1. Check custom JAVA8_HOME if configured
if defined JAVA8_HOME (
    if exist "%JAVA8_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA8_HOME%\bin\java.exe"
        goto :found_java
    )
)

:: 2. Check standard TSO / USMC Java 8 installation paths
if exist "C:\Program Files\Java\jre1.8.0_503\bin\java.exe" (
    set "JAVA_EXE=C:\Program Files\Java\jre1.8.0_503\bin\java.exe"
    goto :found_java
)

if exist "C:\Program Files\Java\jdk1.8\TSO\bin\java.exe" (
    set "JAVA_EXE=C:\Program Files\Java\jdk1.8\TSO\bin\java.exe"
    goto :found_java
)

:: 3. Scan common 64-bit and 32-bit Java 8 directories
for /d %%d in ("C:\Program Files\Java\jre1.8*" "C:\Program Files\Java\jdk1.8*" "C:\Program Files (x86)\Java\jre1.8*" "C:\Program Files (x86)\Java\jdk1.8*") do (
    if exist "%%d\bin\java.exe" (
        set "JAVA_EXE=%%d\bin\java.exe"
        goto :found_java
    )
)

:: 4. Check JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
        goto :found_java
    )
)

:: 5. Check system PATH
where java >nul 2>&1
if !ERRORLEVEL! equ 0 (
    set "JAVA_EXE=java"
    goto :found_java
)

:found_java
if not defined JAVA_EXE goto :error_no_java

:: Ensure JiraApiClient.jar exists
if not exist "JiraApiClient.jar" goto :error_no_jar

echo Starting USMC TSO Jira Client...
echo Runtime: "%JAVA_EXE%"
echo Directory: "%CD%"
echo.

"%JAVA_EXE%" -jar JiraApiClient.jar %*
set "EXIT_CODE=%ERRORLEVEL%"

if %EXIT_CODE% neq 0 (
    echo.
    echo ==============================================================================
    echo Application exited with error code: %EXIT_CODE%
    echo ==============================================================================
    pause
    exit /b %EXIT_CODE%
)

exit /b 0

:error_no_java
echo ==============================================================================
echo ERROR: Java 8 runtime [java.exe] was not found on your system.
echo ==============================================================================
echo JiraApiClient requires Java 8 [JRE 1.8] with JavaFX desktop support.
echo Checked locations:
echo   - JAVA8_HOME\bin\java.exe
echo   - C:\Program Files\Java\jre1.8.0_503\bin\java.exe
echo   - C:\Program Files\Java\jdk1.8\TSO\bin\java.exe
echo   - C:\Program Files\Java\jre1.8*
echo   - C:\Program Files (x86)\Java\jre1.8*
echo.
echo Please set JAVA8_HOME or install Java 8 JRE/JDK.
echo ==============================================================================
pause
exit /b 1

:error_no_jar
echo ==============================================================================
echo ERROR: Application JAR not found: JiraApiClient.jar
echo ==============================================================================
echo Please run "compile and build.bat" to compile and package the client first.
echo ==============================================================================
pause
exit /b 1
