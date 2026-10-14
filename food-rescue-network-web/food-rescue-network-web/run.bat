@echo off
echo ========================================================
echo       Starting Food Rescue Network Console App
echo ========================================================

call "%~dp0mvnw.cmd" compile exec:java -Dexec.mainClass="com.foodrescue.Main"
