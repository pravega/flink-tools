#! /bin/bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
set -ex
: ${1?"You must specify the values.yaml file."}
ROOT_DIR=$(readlink -f $(dirname $0)/../..)
${ROOT_DIR}/scripts/start-job.sh $* \
    -f ${ROOT_DIR}/values/job-defaults/stream-to-console-job.yaml
