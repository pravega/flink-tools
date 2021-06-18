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

: ${1?"You must specify the values.yaml file."}

### set -ex
ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh
: ${NAMESPACE?"You must export NAMESPACE"}

VALUES_FILE="$1"
shift
export RELEASE_NAME=$(basename "${VALUES_FILE}" .yaml)

if [[ "${UNINSTALL}" == "1" ]]; then
    helm del -n ${NAMESPACE} ${RELEASE_NAME} || true
fi

if [[ "${PUBLISH}" != "0" ]]; then
    ${ROOT_DIR}/scripts/publish.sh
    if [[ $? != 0 ]]; then
        printf "\nERROR publish failed\n"
        exit 1;
    fi
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

printf "Waiting for FlinkCluster to start - should start in about 5 minutes\n"
while : ; do
    state=$(kubectl get FlinkCluster -n ${NAMESPACE} ${RELEASE_NAME} | grep ${RELEASE_NAME} | awk '{print $7}')
    if [[ "$state" == "Running" ]]; then
        break;
    fi
done
printf "FlinkCluster started\n"

printf "Waiting for pod/%s-jobmanager-0\n" ${RELEASE_NAME}
kubectl wait -n ${NAMESPACE} pod/${RELEASE_NAME}-jobmanager-0 --for condition=ready --timeout=300s
if [[ $? != 0 ]]; then
    printf "\nERROR %s-jobmanager-0 unable to start\n" ${RELEASE_NAME}
    kubectl describe pod ${RELEASE_NAME}-jobmanager-0 -n ${NAMESPACE}
    exit 1;
fi
printf "pod/%s-jobmanager-0 started\n" ${RELEASE_NAME}

printf "Waiting for pod/%s-taskmanager-0\n" ${RELEASE_NAME}
kubectl wait -n ${NAMESPACE} pod/${RELEASE_NAME}-taskmanager-0 --for condition=ready --timeout=300s
if [[ $? != 0 ]]; then
    printf "\nERROR %s-taskmanager-0 unable to start\n" ${RELEASE_NAME}
    kubectl describe pod ${RELEASE_NAME}-taskmanager-0 -n ${NAMESPACE}
    exit 1;
fi
printf "pod/%s-taskmanager-0 started\n" ${RELEASE_NAME}

while : ; do
    app=$(kubectl get pods -n ${NAMESPACE} | grep ${RELEASE_NAME}-app | cut -d" " -f 1)
    if [[ ! -z "$app" ]]; then
        break;
    fi
done

printf "Waiting for pod/%s\n" ${app}
kubectl wait -n ${NAMESPACE} pod/${app} --for condition=ready --timeout=900s
if [[ $? != 0 ]]; then
    printf "\nERROR pod/%s unable to start\n" ${app} 
    kubectl describe pod ${app} -n ${NAMESPACE}
    exit 1;
fi

kubectl wait -n ${NAMESPACE} pod/${app} --for condition=ready=false --timeout=900s
if [[ $? != 0 ]]; then
    printf "\nERROR pod/%s unable to start\n" ${app} 
    kubectl describe pod ${app} -n ${NAMESPACE}
    exit 1;
fi
printf "pod/%s started\n" ${app}

printf "Waiting for FlinkApplication %s checkpoint - should be completed in 1 minute\n" ${RELEASE_NAME}
while : ; do
    chkpt=$(kubectl get FlinkApplication -n ${NAMESPACE} | grep ${RELEASE_NAME} | awk '{print $6}')
    if [[ "$chkpt" == "OK" ]]; then
        break;
    fi
done
printf "FlinkApplication %s checkpoint - completed\n" ${RELEASE_NAME}

