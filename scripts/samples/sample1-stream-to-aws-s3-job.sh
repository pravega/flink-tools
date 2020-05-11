#! /bin/bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Demonstrates how to create a Helm release to copy a stream to AWS S3.
set -ex
export ROOT_DIR=$(dirname $0)/../..
source ${ROOT_DIR}/scripts/env-local.sh
export RELEASE_NAME=$(basename "$0" .sh)
${ROOT_DIR}/scripts/prepare-helm-install.sh

helm upgrade --install --timeout 600s --debug --wait \
    ${RELEASE_NAME} \
    --namespace ${NAMESPACE} \
    ${ROOT_DIR}/charts/flink-tools \
    -f ${ROOT_DIR}/values/job-defaults/stream-to-file-job.yaml \
    -f ${ROOT_DIR}/values/environments/${HELM_ENVIRONMENT}/flink-on-aws-s3.yaml \
    -f ${ROOT_DIR}/values/environments/${HELM_ENVIRONMENT}/${RELEASE_NAME}.yaml \
    --set clusterConfiguration."s3\.access-key"=${S3_ACCESS_KEY} \
    --set clusterConfiguration."s3\.secret-key"=${S3_SECRET_KEY} \
    $@

watch "kubectl get FlinkApplication -n ${NAMESPACE} ; kubectl get pod -o wide -n ${NAMESPACE}"
