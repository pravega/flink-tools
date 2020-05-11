#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
set -ex
ROOT_DIR=$(dirname $0)/..
source ${ROOT_DIR}/scripts/env-local.sh
export MAVEN_PASSWORD=$(kubectl get secret keycloak-${MAVEN_USERNAME} -n nautilus-system -o jsonpath='{.data.password}' | base64 -d)
export MAVEN_URL=http://localhost:9092/maven2
echo Starting kubectl port forward
kubectl port-forward --namespace ${NAMESPACE} service/repo 9092:80 &
sleep 2s
cd ${ROOT_DIR}
./gradlew publish -PincludeHadoopS3=false
kill %kubectl
