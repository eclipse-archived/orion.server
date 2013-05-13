cd %WORKSPACE%

set java.home=%JAVA_HOME%
c:\java\jdk1.6.0_20\jre\bin\java -version

mkdir test
rd /S /Q %WORKSPACE%\test\eclipse

REM set current proxy configurations in base builder

set settingsDir=%WORKSPACE%\org.eclipse.releng.basebuilder\configuration\.settings
set settingsFile=%settingsDir%\org.eclipse.core.net.prefs

echo settingsDir: %settingsDir% 
echo settingsFile: %settingsFile%

REM mkdir, just in case does not exist yet. 
mkdir %settingsDir%

REM replace contents of org.eclipse.core.net.prefs as exported from CVS
(
echo eclipse.preferences.version=1
echo org.eclipse.core.net.hasMigrated=true
echo proxiesEnabled=true
echo systemProxiesEnabled=true
echo nonProxiedHosts=172.30.206.*
echo proxyData/HTTP/hasAuth=false
echo proxyData/HTTP/host=proxy.eclipse.org
echo proxyData/HTTP/port=9898
echo proxyData/HTTPS/hasAuth=false
echo proxyData/HTTPS/host=proxy.eclipse.org
echo proxyData/HTTPS/port=9898
echo. 
) >%settingsFile%


"c:\java\jdk1.6.0_20\jre\bin\java" -Xmx500m -jar %WORKSPACE%/org.eclipse.releng.basebuilder/plugins/org.eclipse.equinox.launcher.jar -application org.eclipse.equinox.p2.director -repository %repository% -i org.eclipse.orion -d %WORKSPACE%/test/eclipse

cd %WORKSPACE%\test\eclipse

start "Test Orion" /D%WORKSPACE%\test\eclipse %WORKSPACE%\test\eclipse\orion.exe

REM emulate a sleep for 10 seconds to let eclipse get started 
ping -n 11 127.0.0.1 >nul

for /F "tokens=2" %%I in ('TASKLIST /NH /FI "Imagename eq orion.exe"') do set ORION_PID=%%I

"c:\java\jdk1.6.0_20\jre\bin\java" -Xmx500m -jar %WORKSPACE%/org.eclipse.releng.basebuilder/plugins/org.eclipse.equinox.launcher.jar -Dworkspace=%WORKSPACE% -DbuildZip=%buildZip%  -Djava.home=%java.home% -application org.eclipse.ant.core.antRunner -f %WORKSPACE%/releng/org.eclipse.orion.releng/builder/scripts/runTests.xml hudsonJsTests


taskkill /F /T /PID %ORION_PID%
