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
helm repo add stable https://charts.helm.sh/stable
helm upgrade --install --timeout 600s --wait --debug \
    hadoop \
    stable/hadoop \
    --set yarn.nodeManager.replicas=0 \
    $@

kubectl run --image danisla/hadoop:2.9.0 --generator=run-pod/v1 \
    --rm --attach --restart=Never hadoop-$(uuidgen) -- \
    hadoop fs -chmod 777 hdfs://hadoop-hadoop-hdfs-nn.default.svc.cluster.local:9000/
