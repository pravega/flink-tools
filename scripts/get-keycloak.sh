#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
set -ex
ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh
: ${NAMESPACE?"You must export NAMESPACE"}
kubectl get secret ${NAMESPACE}-pravega -n ${NAMESPACE} -o jsonpath="{.data.keycloak\.json}" | base64 -d > ${HOME}/keycloak.json
chmod go-rw ${HOME}/keycloak.json
