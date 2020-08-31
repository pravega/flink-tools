# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0

ROOT_DIR=$(readlink -f $(dirname $0)/..)
# Load environment variables from env-local.sh if it exists.
ENV_LOCAL_SCRIPT=$(dirname $0)/env-local.sh
if [[ -f ${ENV_LOCAL_SCRIPT} ]]; then
    source ${ENV_LOCAL_SCRIPT}
fi
APP_NAME=flink-tools
APP_GROUP_ID=${APP_GROUP_ID:-io.pravega}
APP_ARTIFACT_ID=${APP_ARTIFACT_ID:-flink-tools}
APP_VERSION=${APP_VERSION:-0.2.2}
GRADLE_OPTIONS="${GRADLE_OPTIONS:-"-PincludeHadoopS3=false -Pversion=${APP_VERSION}"}"
FLINK_IMAGE_TAG="1.10.0-2.12-1.2-W2-4-0577915d2"
NEW_IMAGE_TAG="${FLINK_IMAGE_TAG}-hadoop2.8.3"
DOCKER_IMAGE_TAR=flink-${NEW_IMAGE_TAG}.tar
SDP_INSTALL_PATH=${HOME}/desdp
SDP_INSTALL_SCRIPT=${SDP_INSTALL_PATH}/decks-install-linux-amd64
CERTS_PATH=${SDP_INSTALL_PATH}/certs
DOCKER_REGISTRY=$(${SDP_INSTALL_SCRIPT} config list | grep registry |  awk '{ print $2 }')
