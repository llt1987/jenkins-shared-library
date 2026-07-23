#!/bin/bash

# ============================
# Parameters
# ============================
# $1 = Image Name (e.g., rpc)
# $2 = Version Tag (e.g., NOVA7-00-01-316-OL8.10)

IMAGE_NAME=${1:-novastp-19c-ol8}
IMAGE_TAG=${2:-NOVA7-00-01-316-OL8.10}

FULL_IMAGE="harbor.novacmx.com/posttrade/${IMAGE_NAME}:${IMAGE_TAG}"
TAR_FILE="${IMAGE_NAME}.tar"

echo "=========================================="
echo "Image   : ${FULL_IMAGE}"
echo "Tar File: ${TAR_FILE}"
echo "=========================================="

# ============================
# Docker Operations
# ============================

echo "Pulling image..."
docker pull "${FULL_IMAGE}"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker pull failed"
    exit 1
fi

echo "Saving image to tar..."
docker save -o "${TAR_FILE}" "${FULL_IMAGE}"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker save failed"
    exit 1
fi

echo "Removing image..."
docker rmi "${FULL_IMAGE}"

if [ $? -ne 0 ]; then
    echo "WARNING: Failed to remove image"
fi

echo "Image successfully exported to ${TAR_FILE}"

# ============================
# Black Duck Scan
# ============================

echo "Starting Scan..."

java -jar /home/data_jenkins_agent/detect.jar \
 --blackduck.url="https://blackduck-sca.hostednova.com" \
 --blackduck.api.token="OWFlZTMyZjYtMDcxNi00OWM0LTgxMWYtNTM2ZmY1N2M2MDI4OmFhMTU3N2VhLWFjNTMtNGE3Ny1hMGE5LWVjMTNkZWY3ZTdhYg==" \
 --detect.project.name="${IMAGE_NAME}-OL8.10.tar.gz" \
 --detect.project.version.name="latest" \
 --detect.project.version.notes="$IMAGE_TAG" \
 --detect.project.version.update=true \
 --detect.project.group.name="SCA_NOVA_RELEASE_ARTIFACT" \
 --detect.tools=CONTAINER_SCAN \
 --detect.container.scan.file.path="${TAR_FILE}" \
 --detect.container.scan.type=INTELLIGENT \
 --blackduck.trust.cert=true \
 --detect.blackduck.signature.scanner.fail.on.accuracy=false \
 --logging.level.detect=INFO

echo "=========================================="
echo "Process completed successfully!"
echo "=========================================="

rm ${IMAGE_NAME}.tar
