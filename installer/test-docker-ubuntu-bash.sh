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
pushd ${ROOT_DIR}
docker run --rm -it -v ${PWD}:/work -v /tmp/dockertmp:/tmp/dockertmp ubuntu:18.04 bash
popd
