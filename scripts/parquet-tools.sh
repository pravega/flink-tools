#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
set -ex
JAR_FILE=/tmp/parquet-tools-1.11.1.jar
[ -f ${JAR_FILE} ] || wget -O ${JAR_FILE} https://repo1.maven.org/maven2/org/apache/parquet/parquet-tools/1.11.1/parquet-tools-1.11.1.jar
docker run --rm -it -v /tmp:/tmp danisla/hadoop:2.9.0 hadoop jar /tmp/parquet-tools-1.11.1.jar $*
