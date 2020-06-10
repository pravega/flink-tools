#! /bin/bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
set -ex
kubectl delete -n kube-system configmap/coredns || true
kubectl apply -n kube-system -f "$1"
kubectl patch -n kube-system deployment/coredns -p '{"spec":{"replicas":0}}'
kubectl patch -n kube-system deployment/coredns -p '{"spec":{"replicas":3}}'
kubectl get -n kube-system deployment/coredns
date -u
sleep 5s
kubectl get -n kube-system deployment/coredns
