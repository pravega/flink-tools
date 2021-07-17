#! /bin/bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0

# Delete a Flink job.
# It is recommend to start Flink jobs using the scripts in the jobs subdirectory.

: ${1?"You must specify the values.yaml file."}

ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh
: ${NAMESPACE?"You must export NAMESPACE"}

VALUES_FILE="$1"
shift
export RELEASE_NAME=$(basename "${VALUES_FILE}" .yaml)

helm del -n ${NAMESPACE} ${RELEASE_NAME} || true

kubectl wait --for=delete --timeout=900s FlinkCluster/${RELEASE_NAME} -n ${NAMESPACE} || true
