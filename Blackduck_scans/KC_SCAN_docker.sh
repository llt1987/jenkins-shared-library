#!/bin/bash

# ============================
# Parameters
# ============================
# $1 = CLIENT_NAME (LNT, CGL, CMC)
# IMAGE_NAME defaults to novakeycloak

set -e

source blackduck_env.sh
echo "===================PreCheck======================="
echo "Java Home: $JAVA_HOME"
echo "Black Duck URL: $BLACKDUCK_URL"

CLIENT_NAME="$1"

# Validate input
case "$CLIENT_NAME" in
    "LNT")
        IMAGE_TAG="23.0.6-LNT"
                CLIENT_NAME="L&T"
        ;;
    "CGL")
        IMAGE_TAG="26.5.1"
        ;;
    "CMC")
        IMAGE_TAG="26.5.1"
        ;;
    *)
        echo "Usage: $(basename "$0") <CLIENT_NAME>"
        echo "Valid CLIENT_NAME values: LNT, CGL, CMC"
        exit 1
        ;;
esac

IMAGE_NAME="${IMAGE_NAME:-novakeycloak}"

echo "IMAGE_TAG=$IMAGE_TAG"
echo "CLIENT_NAME=$CLIENT_NAME"
echo "detect.project.name=${CLIENT_NAME}_${IMAGE_NAME}_${IMAGE_TAG}.tar.gz"


FULL_IMAGE="harbor.novacmx.com/posttrade/novakeycloak:${IMAGE_TAG}"
TAR_FILE="${IMAGE_NAME}.tar"


echo "Image   : $FULL_IMAGE"
echo "Tar File: $TAR_FILE"
echo "===================PreCheck======================="

# ============================
# Docker Operations
# ============================

echo "Pulling image..."
docker pull "$FULL_IMAGE"

echo "Saving image to tar..."
docker save -o "$TAR_FILE" "$FULL_IMAGE"

echo "Removing image..."
docker rmi "$FULL_IMAGE"



echo "Starting Scan..."

java -jar detect.jar \
  --blackduck.url="$BLACKDUCK_URL" \
  --blackduck.api.token="$BLACKDUCK_TOKEN" \
  --detect.project.name="${CLIENT_NAME}_${IMAGE_NAME}_${IMAGE_TAG}.tar.gz" \
  --detect.project.version.name="latest" \
  --detect.project.version.notes="${IMAGE_TAG}" \
  --detect.project.version.update=true \
  --detect.project.group.name="${CLIENT_NAME}" \
  --detect.tools=CONTAINER_SCAN \
  --detect.container.scan.file.path="${TAR_FILE}" \
  --detect.container.scan.type=INTELLIGENT \
  --blackduck.trust.cert=true \
  --detect.blackduck.signature.scanner.fail.on.accuracy=false \
  --logging.level.detect=DEBUG

rm $TAR_FILE

echo "=========================================="
echo "Process completed successfully!"
echo "=========================================="
