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
export MAVEN_URL="${MAVEN_URL:-http://$(kubectl get ing -n ${NAMESPACE?"You must export NAMESPACE"} repo -o jsonpath='{.spec.rules[0].host}')/maven2}"
export MAVEN_USERNAME="${MAVEN_USERNAME:-desdp}"
export MAVEN_PASSWORD="${MAVEN_PASSWORD:-$(kubectl get secret keycloak-${MAVEN_USERNAME} -n nautilus-system -o jsonpath='{.data.password}' | base64 -d)}"
#export APP_GROUP_ID=io.pravega
#export APP_ARTIFACT_ID=flink-tools
#export APP_VERSION=0.2.0
export APP_ARTIFACT=${ROOT_DIR}/libs/pravega-flink-tools-${APP_VERSION}.jar
cd ${ROOT_DIR}/installer
${ROOT_DIR}/gradle/bin/gradle publish $*
