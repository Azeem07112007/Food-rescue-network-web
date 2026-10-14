@echo off
rem Maven Wrapper script for Food Rescue Network

:checkMaven
set "MAVEN_CMD=%~dp0apache-maven-3.10.0\bin\mvn.cmd"
if exist "%MAVEN_CMD%" (
    call "%MAVEN_CMD%" %*
) else (
    call mvn %*
)
