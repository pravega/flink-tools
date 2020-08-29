#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh
# Set the docker registry with desa install
echo sdp install script $SDP_INSTALL_SCRIPT
$SDP_INSTALL_SCRIPT config set registry ${DOCKER_REGISTRY}

# Push Image to docker registry with desa installer
$SDP_INSTALL_SCRIPT push --input $DOCKER_IMAGE_TAR  --ca-certs-dir $CERTS_PATH

if [ $? -ne 0 ]
then
    echo $DOCKER_IMAGE_TAR failed to push to ${DOCKER_REGISTRY}
else
    echo $DOCKER_IMAGE_TAR  push success!
    cat ${ROOT_DIR}/flink-image/ClusterFlinkImage.yaml | sed "s,\${IMAGE_REF},$DOCKER_REGISTRY:$NEW_IMAGE_TAG,g" | kubectl apply -f -
fi

