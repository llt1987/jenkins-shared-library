@echo off
setlocal

REM ============================
REM Parameters
REM ============================
REM %1 = Image Name (e.g., nova)
REM %2 = Version Tag (e.g., NOVA7-00-01-254)

set IMAGE_NAME=%1
set IMAGE_TAG=%2

if %1 == nova	  		goto nova
if %1 == RHEL7 			goto RHEL7	
if %1 == RHEL8 			goto RHEL8
if %1 == RHEL9 			goto RHEL9
if %1 == OL8  			goto OL8


:nova
set IMAGE_NAME=nova.zip
goto continue

:RHEL7
set IMAGE_NAME=Linux-Nexus-nova-dev-regtest-rh7-2a_19c.tar.gz
goto continue

:RHEL8
set IMAGE_NAME=Linux-Nexus-sg-nov-rhel8-03_19c.tar.gz
goto continue

:RHEL9
set IMAGE_NAME=Linux-Nexus-sg-nov-reg-rh9_19c.tar.gz
goto continue

:OL8
set IMAGE_NAME=Linux-Nexus-sg-nov-ol08-04_19c.tar.gz
goto continue


:continue
REM Defaults (optional safety)
if "%IMAGE_TAG%"=="" set IMAGE_TAG=NOVA7-00-01-254

echo ==========================================
echo Scanning	: %IMAGE_NAME%
echo Tag		: %IMAGE_TAG%
echo ==========================================

REM ============================
REM Copy New nova.zip from ftp
REM ============================

rem Step 1: rename if file exists
if exist %IMAGE_NAME% (
    echo Renaming existing %IMAGE_NAME%...
    ren %IMAGE_NAME% %IMAGE_NAME%.old
)

rem Step 2: copy new file
echo ==============================
echo Copying %IMAGE_NAME% from network
echo ==============================

set SOURCE=\\shared.novacmx.local\Novabld\novaarchive\700-BUILDS\%IMAGE_TAG%\64bit\%IMAGE_NAME%
set DEST=%CD%

echo Source: %SOURCE%
echo Destination: %DEST%

echo Copying new file...
copy "%SOURCE%" "%DEST%"


if %ERRORLEVEL% LEQ 3 (
    echo Copy completed successfully
) else (
    echo Copy failed with error code %ERRORLEVEL%
)


rem Step 3: delete old file (optional cleanup)
if exist %IMAGE_NAME%.old (
    echo Deleting %IMAGE_NAME%.old file...
    del /F /Q %IMAGE_NAME%.old
)


REM ============================
REM Black Duck Scan
REM ============================

echo Starting Scan...

@echo "Starting Scan"
java -jar "C:\Source\blackduck\detect.jar" ^
 --blackduck.url="https://blackduck-sca.hostednova.com" ^
 --blackduck.api.token=OWFlZTMyZjYtMDcxNi00OWM0LTgxMWYtNTM2ZmY1N2M2MDI4OmFhMTU3N2VhLWFjNTMtNGE3Ny1hMGE5LWVjMTNkZWY3ZTdhYg== ^
 --detect.project.name=%IMAGE_NAME% ^
 --detect.project.version.name="%IMAGE_TAG%" ^
 --detect.project.group.name="SCA_NOVA_RELEASE_ARTIFACT" ^
 --detect.tools=DETECTOR,SIGNATURE_SCAN,BINARY_SCAN ^
 --detect.binary.scan.file.path="%IMAGE_NAME%" ^
 --blackduck.trust.cert=true ^
 --detect.blackduck.signature.scanner.fail.on.accuracy=false ^
 --detect.blackduck.scan.mode=INTELLIGENT ^
 --logging.level.detect=DEBUG ^


if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Scan failed
    exit /b 1
)

echo ==========================================
echo Process completed successfully!
echo ==========================================
rem del %TAR_FILE%

endlocal
