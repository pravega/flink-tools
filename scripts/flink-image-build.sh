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
IMAGE_TAG=claudiofahey/flink:1.10.0-2.12-1.2-W2-4-0577915d2-hadoop2.8.3
mkdir -p ${ROOT_DIR}/flink-image/files/opt/flink/lib
wget -O ${ROOT_DIR}/flink-image/files/opt/flink/lib/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar \
https://repo.maven.apache.org/maven2/org/apache/flink/flink-shaded-hadoop-2-uber/2.8.3-10.0/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar
docker build --tag ${IMAGE_TAG} ${ROOT_DIR}/flink-image
docker push ${IMAGE_TAG}
