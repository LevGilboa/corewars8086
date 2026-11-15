@echo off
rem Launches CodeGuru Extreme. Finds the jar in bin\ regardless of its version.
cd /d "%~dp0"
set "JAR="
for %%f in (bin\corewars8086-*.jar) do set "JAR=%%f"
if not defined JAR (
    echo No corewars8086 jar found in bin\ 1>&2
    exit /b 1
)
java -jar "%JAR%" --noparallel --zombieSpeed 2 %*
