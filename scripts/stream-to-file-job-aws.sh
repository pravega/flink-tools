#! /bin/bash
# Demonstrates how to create a Helm release to copy a stream to a AWS S3.

set -ex
ROOT_DIR=$(dirname $0)/..
source ${ROOT_DIR}/scripts/env-local.sh

RELEASE_NAME=stream-to-file-job-aws

helm del -n ${NAMESPACE} ${RELEASE_NAME} $@ || true

kubectl wait --for=delete --timeout=900s FlinkCluster/${RELEASE_NAME} -n ${NAMESPACE} || true

helm upgrade --install --timeout 600s --wait --debug \
    ${RELEASE_NAME} \
    --namespace ${NAMESPACE} \
    ${ROOT_DIR}/charts/stream-to-file-job \
    --set appParameters.input-stream=video2 \
    --set appParameters.output=s3a://${S3_BUCKET}/${RELEASE_NAME} \
    --set clusterConfiguration."s3\.endpoint"=${S3_ENDPOINT} \
    --set clusterConfiguration."s3\.path\.style\.access"=false \
    --set clusterConfiguration."s3\.access-key"=${S3_ACCESS_KEY} \
    --set clusterConfiguration."s3\.secret-key"=${S3_SECRET_KEY} \
    --set clusterConfiguration."state\.checkpoints\.dir"=s3p://${S3_BUCKET}/.flink/${RELEASE_NAME}/checkpoints \
    --set clusterConfiguration."state\.savepoints\.dir"=s3p://${S3_BUCKET}/.flink/savepoints \
    --set clusterConfiguration."high-availability\.storageDir"=s3p://${S3_BUCKET}/.flink/ha \
    $@
