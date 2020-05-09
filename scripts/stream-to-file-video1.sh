#! /bin/bash
# Demonstrates how to create a Helm release to copy a stream to a file.

set -ex
ROOT_DIR=$(dirname $0)/..
source ${ROOT_DIR}/scripts/env-local.sh

helm upgrade --install --timeout 600s --wait --debug \
    stream-to-file-job-video1 \
    --namespace ${NAMESPACE} \
    ${ROOT_DIR}/charts/stream-to-file-job \
    --set appParameters.input-stream=video1 \
    --set appParameters.output=s3a://cf03-examples-fd3c09cd-e06a-4ff6-aa6a-e7675917394a/stream-to-file--examples-video1-2
    $@
