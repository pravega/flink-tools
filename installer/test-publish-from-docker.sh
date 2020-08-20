#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
set -ex
cd
export MAVEN_URL="${MAVEN_URL:-http://repo.examples.jubilee.em.sdp.hop.lab.emc.com/maven2}"
export MAVEN_USERNAME="${MAVEN_USERNAME:-desdp}"
export MAVEN_PASSWORD="${MAVEN_PASSWORD:-password}"
rm -rf flink-tools
tar -xzf /tmp/dockertmp/flink-tools.tgz
flink-tools/scripts/publish-from-jar.sh
