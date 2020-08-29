#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0

# Build Flink Tools locally, then copy and install it on a remote system.

set -ex
ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh
INSTALLER_TGZ=${APP_NAME}-${APP_VERSION}.tgz

: ${SSH_HOST?"You must export SSH_HOST"}

${ROOT_DIR}/scripts/build-installer.sh
rsync -e "ssh ${SSH_OPTS}" -v -c --progress ${ROOT_DIR}/build/installer/${INSTALLER_TGZ} ${SSH_HOST}:~/desdp/
ssh -t ${SSH_OPTS} ${SSH_HOST} "cd ~/desdp && tar -xzf ${INSTALLER_TGZ}"
