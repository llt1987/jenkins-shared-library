#!/bin/bash

if [ $# -ne 1 ]; then
    echo "Usage: $0 <NOVA_VERSION>"
    exit 1
fi

NOVA_VERSION="$1"


OUTPUT_FILE="binhashfile-${NOVA_VERSION}.xml"

HASH_DATE=$(date '+%a %b %d %H:%M:%S %Y')

OS=$(uname -s)
MACHINE=$(uname -n)

NOVA_COMPONENTS=("novagui" "novaserver" "novastp-19c-ol8")

echo "Pulling NTIER Docker images with prefix 'harbor.novacmx.com/posttrade'"
docker login harbor.novacmx.com
docker pull harbor.novacmx.com/posttrade/novakeycloak:23.0.6
docker pull harbor.novacmx.com/posttrade/nginx:1.25.3
for IMAGE_NAME in "${NOVA_COMPONENTS[@]}"; do
        echo harbor.novacmx.com/posttrade/$IMAGE_NAME:$NOVA_VERSION
        if docker pull "harbor.novacmx.com/posttrade/$IMAGE_NAME:$NOVA_VERSION" >/dev/null 2>&1; then
            echo "Successfully pulled $IMAGE_NAME"
        else
            echo "Error: Failed to pull $IMAGE_NAME"
        fi
done

DOCKER_IMAGES=$(docker images --format "{{.Repository}}.tar.gz {{.Tag}} {{.Digest}}" | grep '^registryapac\.contemi\.com/posttrade/' | sort -u)

cat << EOF > "$OUTPUT_FILE"
<?xml version="1.0" encoding="utf-8"?>
<!--
Build version : $BUILD_VERSION
Hash Date : $HASH_DATE
Operating System : $OS
Machine : $MACHINE
-->
<Files>
EOF

ID=0

while IFS=' ' read -r IMAGE_NAME TAG IMAGE_HASH; do
    FILE_NAME=$(basename "$IMAGE_NAME ($TAG)")

    cat << EOF >> "$OUTPUT_FILE"
  <File>
    <Id>$ID</Id>
    <Directory></Directory>
    <FileName>$FILE_NAME</FileName>
    <Hash>$IMAGE_HASH</Hash>
  </File>
EOF
    ((ID++))
done <<< "$DOCKER_IMAGES"

echo "</Files>" >> "$OUTPUT_FILE"

echo "Generated XML file: $OUTPUT_FILE"
