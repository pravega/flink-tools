# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0

# This will create a Flink image with extra files.
# It will be used to include the Hadoop libraries.

ARG FLINK_IMAGE_REPO
ARG FLINK_IMAGE_TAG
FROM ${FLINK_IMAGE_REPO}:${FLINK_IMAGE_TAG}
COPY files/ /
