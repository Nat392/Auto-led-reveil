@echo off
setlocal

REM Minimal Gradle wrapper launcher that uses the Java-based wrapper
set DIR=%~dp0

where java >nul 2>nul
if errorlevel 1 (
	echo ERROR: Java not found in PATH. Install JDK 17+ or set JAVA_HOME.
	exit /b 1
)

java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%