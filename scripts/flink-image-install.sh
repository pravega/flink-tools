#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0

set -ex
ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh
DOCKER_IMAGE_TAR=${SDP_INSTALL_PATH}/${DOCKER_IMAGE_TAR}
[ -f "${DOCKER_IMAGE_TAR}" ] || { echo "File ${DOCKER_IMAGE_TAR} not found" ; exit 1 ;}
[ -f "${SDP_INSTALL_EXECUTABLE}" ] || { echo "File ${SDP_INSTALL_EXECUTABLE} not found" ; exit 1 ;}
[ -d "${CERTS_PATH}" ] || { echo "Directory ${CERTS_PATH} not found" ; exit 1 ;}
# Push image to Docker registry with decks-installer.
${SDP_INSTALL_EXECUTABLE} push --input ${DOCKER_IMAGE_TAR}  --ca-certs-dir ${CERTS_PATH}
cat ${ROOT_DIR}/flink-image/RuntimeFlinkImage.yaml | sed "s,\${IMAGE_REF},${DOCKER_REGISTRY}/flink:${NEW_IMAGE_TAG},g" | kubectl apply -f -
echo Flink image loaded from ${DOCKER_IMAGE_TAR}.
