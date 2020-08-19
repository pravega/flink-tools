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
source ${ROOT_DIR}/scripts/env.sh
: ${NAMESPACE?"You must export NAMESPACE"}
helm repo add stable https://kubernetes-charts.storage.googleapis.com
helm upgrade --install --timeout 600s --wait --debug \
    hadoop \
    --namespace ${NAMESPACE} \
    stable/hadoop \
    --set yarn.nodeManager.replicas=0 \
    $@

kubectl run --image danisla/hadoop:2.9.0 --generator=run-pod/v1 \
    --rm --attach --restart=Never hadoop-$(uuidgen) -n ${NAMESPACE} -- \
    hadoop fs -chmod 777 hdfs://hadoop-hadoop-hdfs-nn.examples.svc.cluster.local:9000/
