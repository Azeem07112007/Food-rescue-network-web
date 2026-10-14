@echo off
setlocal
cd /d "%~dp0"
call mvnw.cmd -q compile exec:java -Dexec.mainClass="com.foodrescue.WebApplication"
endlocal
