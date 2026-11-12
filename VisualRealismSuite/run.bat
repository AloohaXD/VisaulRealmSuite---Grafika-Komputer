@echo off
rem Jalankan aplikasi (Windows). Perlu JDK 17+ dan Maven 3.8+.
cd /d "%~dp0"
mvn -q compile exec:exec
