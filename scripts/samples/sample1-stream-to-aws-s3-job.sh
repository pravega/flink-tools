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
ROOT_DIR=$(dirname $0)/../..
source ${ROOT_DIR}/scripts/env-local.sh

RELEASE_NAME=$(basename "$0" .sh)

if [[ "${UNINSTALL}" == "1" ]]; then
    helm del -n ${NAMESPACE} ${RELEASE_NAME} $@ || true
fi

if [[ "${PUBLISH}" != "0" ]]; then
    ${ROOT_DIR}/scripts/publish.sh
fi

if [[ "${UNINSTALL}" == "1" ]]; then
    kubectl wait --for=delete --timeout=900s FlinkCluster/${RELEASE_NAME} -n ${NAMESPACE} || true
fi

helm upgrade --install --timeout 600s --debug \
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
