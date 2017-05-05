@echo off
call mvn clean install -DskipTests=true -U
pause