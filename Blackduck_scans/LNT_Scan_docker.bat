rem java -jar "C:\Source\Software\Sw-in-use\hub-2026.4.0\detect.jar" --blackduck.url=https://blackduck-sca.hostednova.com --blackduck.api.token=ZmYxOWE4YmItMmM5MC00Njk5LWE4OTYtODBmMjQxYjgzZThkOjczZDRiY2E4LTUzOTAtNDE1My1iOWJhLTg4MmUzNjIyN2JjZQ== --detect.project.name=uat_lnt_novagui.tar.gz --detect.project.version.name="NOVA6-00-01-720-LNT-P48" --detect.project.group.name="L&T" --detect.tools=DOCKER --detect.docker.tar="novagui.tar.gz" --blackduck.trust.cert=true --detect.blackduck.signature.scanner.fail.on.accuracy=false --logging.level.detect=DEBUG
rem java -jar "C:\Source\Software\Sw-in-use\hub-2026.4.0\detect.jar" --blackduck.url="https://blackduck-sca.hostednova.com" --blackduck.api.token="ZmYxOWE4YmItMmM5MC00Njk5LWE4OTYtODBmMjQxYjgzZThkOjczZDRiY2E4LTUzOTAtNDE1My1iOWJhLTg4MmUzNjIyN2JjZQ==" --detect.project.name="Test" --detect.project.version.name="1.0.0" --detect.source.path="novantierserver\novaserver" --detect.tools=SIGNATURE_SCAN --blackduck.trust.cert=true --detect.blackduck.signature.scanner.fail.on.accuracy=false --logging.level.detect=DEBUG

@echo off
setlocal

REM ============================
REM Parameters
REM ============================
REM %1 = Image Name (e.g., novagui)
REM %2 = Version Tag (e.g., NOVA6-00-01-720-LNT-P48)

set IMAGE_NAME=%1
set IMAGE_TAG=%2

REM Defaults (optional safety)
if "%IMAGE_NAME%"=="" set IMAGE_NAME=novagui
if "%IMAGE_TAG%"=="" set IMAGE_TAG=NOVA6-00-01-720-LNT-P48

set FULL_IMAGE=harbor.novacmx.com/posttrade/%IMAGE_NAME%:%IMAGE_TAG%
set TAR_FILE=%IMAGE_NAME%.tar

echo ==========================================
echo Image   : %FULL_IMAGE%
echo Tar File: %TAR_FILE%
echo ==========================================

REM ============================
REM Docker Operations
REM ============================

echo Pulling image...
docker pull %FULL_IMAGE%

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Docker pull failed
    exit /b 1
)

echo Saving image to tar...
docker save -o %TAR_FILE% %FULL_IMAGE%

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Docker save failed
    exit /b 1
)

echo Removing image...
docker rmi %FULL_IMAGE%

REM ============================
REM Black Duck Scan
REM ============================

echo Starting Scan...

@echo "Starting Scan"
java -jar "C:\Source\Software\Sw-in-use\hub-2026.4.0\detect.jar" ^
 --blackduck.url="https://blackduck-sca.hostednova.com" ^
 --blackduck.api.token=ZmYxOWE4YmItMmM5MC00Njk5LWE4OTYtODBmMjQxYjgzZThkOjczZDRiY2E4LTUzOTAtNDE1My1iOWJhLTg4MmUzNjIyN2JjZQ== ^
 --detect.project.name=uat_lnt_%IMAGE_NAME%.tar.gz ^
 --detect.project.version.name="%IMAGE_TAG%" ^
 --detect.project.group.name="L&T" ^
 --detect.tools=DOCKER ^
 --detect.docker.tar="%TAR_FILE%" ^
 --blackduck.trust.cert=true ^
 --detect.blackduck.signature.scanner.fail.on.accuracy=false ^
 --logging.level.detect=DEBUG ^


if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Scan failed
    exit /b 1
)

echo ==========================================
echo Process completed successfully!
echo ==========================================

endlocal
