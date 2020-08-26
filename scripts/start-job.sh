#! /bin/bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0

# Publish and start a Flink job.
# It is recommend to start Flink jobs using the scripts in the jobs subdirectory.

set -ex

: ${1?"You must specify the values.yaml file."}

ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh
: ${NAMESPACE?"You must export NAMESPACE"}

VALUES_FILE="$1"
shift
export RELEASE_NAME=$(basename "${VALUES_FILE}" .yaml)

if [[ "${UNINSTALL}" == "1" ]]; then
    helm del -n ${NAMESPACE} ${RELEASE_NAME} $@ || true
fi

if [[ "${PUBLISH}" != "0" ]]; then
    ${ROOT_DIR}/scripts/publish.sh
fi

if [[ "${UNINSTALL}" == "1" ]]; then
    kubectl wait --for=delete --timeout=900s FlinkCluster/${RELEASE_NAME} -n ${NAMESPACE} || true
fi

helm upgrade --install --timeout 600s --debug --wait \
    ${RELEASE_NAME} \
    ${ROOT_DIR}/charts/flink-tools \
    --namespace ${NAMESPACE} \
    -f "${VALUES_FILE}" \
    --set "mavenCoordinate.artifact=${APP_ARTIFACT_ID}" \
    --set "mavenCoordinate.group=${APP_GROUP_ID}" \
    --set "mavenCoordinate.version=${APP_VERSION}" \
    $@

watch "kubectl get FlinkApplication -n ${NAMESPACE} ; kubectl get pod -o wide -n ${NAMESPACE}"
