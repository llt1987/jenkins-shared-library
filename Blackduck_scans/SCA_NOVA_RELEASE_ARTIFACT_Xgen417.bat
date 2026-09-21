@echo off
setlocal

REM ============================
REM Parameters
REM ============================
REM %1 = Image Name (e.g., nova)
REM %2 = Version Tag (e.g., NOVA7-00-01-254)

set IMAGE_NAME=%1
set IMAGE_TAG=%2


if %1 == xgen  			goto xgen415
if %1 == xgen417  		goto xgen417


:xgen415
set IMAGE_NAME=xgen
set SOURCE=\\shared.novacmx.local\Novabld\novaarchive\700-BUILDS\%IMAGE_TAG%\%IMAGE_NAME%\4.15
set DEST=%CD%\xgen
md xgen

echo Source: %SOURCE%
echo Destination: %DEST%

echo Copying new file...
copy "%SOURCE%\*" "%DEST%"


echo Starting Scan...

@echo "Starting Scan"
java -jar "C:\Source\blackduck\detect.jar" ^
 --blackduck.url="https://blackduck-sca.hostednova.com" ^
 --blackduck.api.token=OWFlZTMyZjYtMDcxNi00OWM0LTgxMWYtNTM2ZmY1N2M2MDI4OmFhMTU3N2VhLWFjNTMtNGE3Ny1hMGE5LWVjMTNkZWY3ZTdhYg== ^
 --detect.project.name=%IMAGE_NAME%415 ^
 --detect.project.version.name="%IMAGE_TAG%" ^
 --detect.project.group.name="SCA_NOVA_RELEASE_ARTIFACT" ^
 --detect.tools=DETECTOR,SIGNATURE_SCAN ^
 --detect.binary.scan.file.path="%DEST%" ^
 --blackduck.trust.cert=true ^
 --detect.blackduck.signature.scanner.fail.on.accuracy=false ^
 --detect.blackduck.scan.mode=INTELLIGENT ^
 --logging.level.detect=DEBUG ^


if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Scan failed
    exit /b 1
)

goto end


:xgen417
set IMAGE_NAME=X-Gen-4.17-Linux-64bit.tar.gz
set SOURCE=\\shared.novacmx.local\Novabld\novaarchive\xgen_417_binary
set DEST=%CD%\xgen417
md xgen417

echo Source: %SOURCE%
echo Destination: %DEST%

echo Copying new file...
copy "%SOURCE%\*" "%DEST%"


echo Starting Scan...

@echo "Starting Scan"
java -jar "C:\Source\blackduck\detect.jar" ^
 --blackduck.url="https://blackduck-sca.hostednova.com" ^
 --blackduck.api.token=OWFlZTMyZjYtMDcxNi00OWM0LTgxMWYtNTM2ZmY1N2M2MDI4OmFhMTU3N2VhLWFjNTMtNGE3Ny1hMGE5LWVjMTNkZWY3ZTdhYg== ^
 --detect.project.name=%IMAGE_NAME% ^
 --detect.project.version.name="latest" ^
 --detect.project.group.name="SCA_NOVA_RELEASE_ARTIFACT" ^
 --detect.tools=DETECTOR,SIGNATURE_SCAN ^
 --detect.binary.scan.file.path=%IMAGE_NAME% ^
 --blackduck.trust.cert=true ^
 --detect.blackduck.signature.scanner.fail.on.accuracy=false ^
 --detect.blackduck.scan.mode=INTELLIGENT ^
 --logging.level.detect=DEBUG ^


if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Scan failed
    exit /b 1
)


@echo "Starting Scan"
java -jar "C:\Source\blackduck\detect.jar" ^
 --blackduck.url="https://blackduck-sca.hostednova.com" ^
 --blackduck.api.token=OWFlZTMyZjYtMDcxNi00OWM0LTgxMWYtNTM2ZmY1N2M2MDI4OmFhMTU3N2VhLWFjNTMtNGE3Ny1hMGE5LWVjMTNkZWY3ZTdhYg== ^
 --detect.project.name=X-Gen-4.17-Linux-64-VALANTIC-p006.tar.gz ^
 --detect.project.version.name="latest" ^
 --detect.project.group.name="SCA_NOVA_RELEASE_ARTIFACT" ^
 --detect.tools=DETECTOR,SIGNATURE_SCAN ^
 --detect.binary.scan.file.path="X-Gen-4.17-Linux-64-VALANTIC-p006.tar.gz" ^
 --blackduck.trust.cert=true ^
 --detect.blackduck.signature.scanner.fail.on.accuracy=false ^
 --detect.blackduck.scan.mode=INTELLIGENT ^
 --logging.level.detect=DEBUG ^


if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Scan failed
    exit /b 1
)

goto end

:end

echo ==========================================
echo Process completed successfully!
echo ==========================================
rem del %TAR_FILE%

endlocal
