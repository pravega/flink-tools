#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0

# This will build a custom Flink image that includes Hadoop.
# It will create a Docker tar file that can be loaded with "docker load".

set -ex
ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh
DOCKER_IMAGE_TAR=${ROOT_DIR}/build/${DOCKER_IMAGE_TAR}
FLINK_IMAGE_REPO="${FLINK_IMAGE_REPO:-devops-repo.isus.emc.com:8116/nautilus/flink}"
mkdir -p ${ROOT_DIR}/build
mkdir -p ${ROOT_DIR}/flink-image/files/opt/flink/lib
HADOOP_JAR=${ROOT_DIR}/flink-image/files/opt/flink/lib/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar
[ -f ${HADOOP_JAR} ] || wget -O ${HADOOP_JAR} \
    https://repo.maven.apache.org/maven2/org/apache/flink/flink-shaded-hadoop-2-uber/2.8.3-10.0/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar
docker build \
    --tag ${FLINK_IMAGE_REPO}:${NEW_IMAGE_TAG} \
    --build-arg "FLINK_IMAGE_REPO=${FLINK_IMAGE_REPO}" \
    --build-arg "FLINK_IMAGE_TAG=${FLINK_IMAGE_TAG}" \
    ${ROOT_DIR}/flink-image
docker save ${FLINK_IMAGE_REPO}:${NEW_IMAGE_TAG} > ${DOCKER_IMAGE_TAR}
ls -lh ${DOCKER_IMAGE_TAR}
