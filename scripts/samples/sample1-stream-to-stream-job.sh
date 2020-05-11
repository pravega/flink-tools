#! /bin/bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
set -ex
export ROOT_DIR=$(dirname $0)/../..
source ${ROOT_DIR}/scripts/env-local.sh
export RELEASE_NAME=$(basename "$0" .sh)
${ROOT_DIR}/scripts/prepare-helm-install.sh

helm upgrade --install --timeout 600s --debug --wait \
    ${RELEASE_NAME} \
    --namespace ${NAMESPACE} \
    ${ROOT_DIR}/charts/flink-tools \
    -f ${ROOT_DIR}/values/job-defaults/stream-to-stream-job.yaml \
    -f ${ROOT_DIR}/values/environments/${HELM_ENVIRONMENT}/${RELEASE_NAME}.yaml \
    $@

watch "kubectl get FlinkApplication -n ${NAMESPACE} ; kubectl get pod -o wide -n ${NAMESPACE}"
