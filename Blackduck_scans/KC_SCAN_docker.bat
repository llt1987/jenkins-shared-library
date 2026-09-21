REM ============================
REM Parameters
REM ============================
REM %1 = CLIENT_NAME (e.g., "L&T", CGL, CMC)
REM 	 IMAGE_NAME (default to novakeycloak)

@echo off
setlocal

if /I "%~1"=="L&T" goto VALID
if /I "%~1"=="CGL" goto VALID
if /I "%~1"=="CMC" goto VALID

echo Usage: %~nx0 ^<CLIENT_NAME^>
echo Valid CLIENT_NAME values: "L&T", CGL, CMC
exit /b 1

:VALID
set "CLIENT_NAME=%~1"

if /I "%CLIENT_NAME%"=="L&T" (
    set "IMAGE_TAG=23.0.6-LNT"
) else (
    if /I "%CLIENT_NAME%"=="CGL" (
        set "IMAGE_TAG=26.5.1"
    ) else (
        if /I "%CLIENT_NAME%"=="CMC" (
            set "IMAGE_TAG=26.5.1"
        )
    )
)

if "%IMAGE_NAME%"=="" set "IMAGE_NAME=novakeycloak"

echo IMAGE_TAG=%IMAGE_TAG%
echo CLIENT_NAME="%CLIENT_NAME%"
echo detect.project.name="%CLIENT_NAME%_%IMAGE_NAME%_%IMAGE_TAG%.tar.gz"



rem if "%IMAGE_TAG%"=="" set IMAGE_TAG=NOVA6-00-01-720-LNT-P48

set FULL_IMAGE=harbor.novacmx.com/posttrade/novakeycloak:%IMAGE_TAG%
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
 --detect.project.name=%CLIENT_NAME%_%IMAGE_NAME%_%IMAGE_TAG%.tar.gz ^
 --detect.project.version.name="latest" ^
 --detect.project.group.name=%CLIENT_NAME% ^
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
