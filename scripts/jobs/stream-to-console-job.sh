#! /bin/bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
set -ex

: ${1?"You must specify the values.yaml file."}

export ROOT_DIR=$(dirname $0)/../..
source ${ROOT_DIR}/scripts/env.sh
: ${NAMESPACE?"You must export NAMESPACE"}
VALUES_FILE="$1"
shift
export RELEASE_NAME=$(basename "${VALUES_FILE}" .yaml)

${ROOT_DIR}/scripts/prepare-helm-install.sh

helm upgrade --install --timeout 600s --debug --wait \
    ${RELEASE_NAME} \
    ${ROOT_DIR}/charts/flink-tools \
    --namespace ${NAMESPACE} \
    -f ${ROOT_DIR}/values/job-defaults/stream-to-console-job.yaml \
    -f "${VALUES_FILE}" \
    $@

watch "kubectl get FlinkApplication -n ${NAMESPACE} ; kubectl get pod -o wide -n ${NAMESPACE}"
