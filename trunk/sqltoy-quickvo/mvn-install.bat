@echo off
set list=mysql db2 mssql oracle postgre
(for %%a in (%list%) do (
   mvn clean package install -Dmaven.test.skip=true -f pom-%%a.xml
)) 
@pause