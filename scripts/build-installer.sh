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
APP_NAME=flink-tools
INSTALLER_BUILD_DIR=${ROOT_DIR}/build/installer/${APP_NAME}
INSTALLER_TGZ=${ROOT_DIR}/build/installer/${APP_NAME}.tgz

# Delete output directories and files.
rm -rf ${INSTALLER_BUILD_DIR} ${INSTALLER_TGZ}
mkdir -p ${INSTALLER_BUILD_DIR}

# Build Flink application JAR.
${ROOT_DIR}/gradlew -p ${ROOT_DIR} shadowJar

# Download and extract Gradle.
GRADLE_VERSION=6.3
GRADLE_FILE=${ROOT_DIR}/build/installer/gradle-${GRADLE_VERSION}-bin.zip
[ -f ${GRADLE_FILE} ] || wget -O ${GRADLE_FILE} https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip
unzip -q -d ${INSTALLER_BUILD_DIR} ${GRADLE_FILE}
mv -v ${INSTALLER_BUILD_DIR}/gradle-${GRADLE_VERSION} ${INSTALLER_BUILD_DIR}/gradle

# Copy Flink application JAR.
mkdir -p ${INSTALLER_BUILD_DIR}/flink-tools/build
cp -v \
  ${ROOT_DIR}/flink-tools/build/libs \
  ${INSTALLER_BUILD_DIR}/flink-tools/build

# Copy other files required for an offline install.
cp -rv \
  ${ROOT_DIR}/charts \
  ${ROOT_DIR}/flink-image \
  ${ROOT_DIR}/installer \
  ${ROOT_DIR}/scripts \
  ${ROOT_DIR}/test \
  ${ROOT_DIR}/values \
  ${ROOT_DIR}/LICENSE \
  ${ROOT_DIR}/README.md \
  ${INSTALLER_BUILD_DIR}/

# Create installer archive.
tar -C ${INSTALLER_BUILD_DIR}/.. -czf ${INSTALLER_TGZ} ${APP_NAME}
tar -tzvf ${INSTALLER_TGZ}

# Copy to testing directory.
#TEST_DIR=/tmp/dockertmp
#mkdir -p ${TEST_DIR}
#cp ${INSTALLER_TGZ} ${TEST_DIR}/
