#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0

# Build and publish the Java JAR to the Maven repo on SDP.

set -ex
ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh

# If not specified, get Maven repo parameters using kubectl.
if [[ $(kubectl get ing -n ${NAMESPACE} repo -o jsonpath="{.spec.tls}") == "" ]]; then
    export MAVEN_PROTOCOL=http
else
    export MAVEN_PROTOCOL=https
fi
export MAVEN_URL="${MAVEN_URL:-${MAVEN_PROTOCOL}://$(kubectl get ing -n ${NAMESPACE?"You must export NAMESPACE"} repo -o jsonpath='{.spec.rules[0].host}')/maven2}"
export MAVEN_USERNAME="${MAVEN_USERNAME:-desdp}"
export MAVEN_PASSWORD="${MAVEN_PASSWORD:-$(kubectl get secret keycloak-${MAVEN_USERNAME} -n nautilus-system -o jsonpath='{.data.password}' | base64 -d)}"

export APP_ARTIFACT=${ROOT_DIR}/flink-tools/build/libs/pravega-flink-tools-${APP_VERSION}.jar
export APP_GROUP_ID
export APP_ARTIFACT_ID
export APP_VERSION

if [[ -f ${ROOT_DIR}/gradle/bin/gradle && -f ${APP_ARTIFACT} ]]; then
    echo publish.sh: Publishing from pre-built file ${APP_ARTIFACT}
    cd ${ROOT_DIR}/installer
    ${ROOT_DIR}/gradle/bin/gradle publish ${GRADLE_OPTIONS}
else
    echo publish.sh: Building and publishing from source
    cd ${ROOT_DIR}
    ./gradlew publish ${GRADLE_OPTIONS}
fi

echo publish.sh: Successfully published to ${MAVEN_URL}
