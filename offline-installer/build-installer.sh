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
INSTALLER_NAME=flink-tools-installer
INSTALLER_BUILD_DIR=${ROOT_DIR}/build/${INSTALLER_NAME}
INSTALLER_TGZ=${ROOT_DIR}/build/${INSTALLER_NAME}.tgz

# Delete output directories and files.
rm -rf ${INSTALLER_BUILD_DIR} ${INSTALLER_TGZ}
mkdir -p ${INSTALLER_BUILD_DIR}

# Build Flink application JAR.
${ROOT_DIR}/gradlew -p ${ROOT_DIR} shadowJar

# Download and extract Gradle.
GRADLE_VERSION=6.3
GRADLE_FILE=/tmp/gradle-${GRADLE_VERSION}-bin.zip
[ -f ${GRADLE_FILE} ] || wget -O ${GRADLE_FILE} https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip
unzip -q -d ${INSTALLER_BUILD_DIR} ${GRADLE_FILE}
mv -v ${INSTALLER_BUILD_DIR}/gradle-${GRADLE_VERSION} ${INSTALLER_BUILD_DIR}/gradle

# Copy other files required for offline install.
cp -v \
  ${ROOT_DIR}/flink-tools/build/libs/* \
  ${ROOT_DIR}/offline-installer/build.gradle \
  ${ROOT_DIR}/offline-installer/publish.sh \
  ${ROOT_DIR}/offline-installer/settings.gradle \
  ${INSTALLER_BUILD_DIR}

# Create installer archive.
tar -C ${ROOT_DIR}/build -czf ${INSTALLER_TGZ} ${INSTALLER_NAME}
tar -tzvf ${INSTALLER_TGZ}

# Extract for testing.
TEST_DIR=${ROOT_DIR}/offline-installer/test
rm -rf ${TEST_DIR}
mkdir -p ${TEST_DIR}
cp ${INSTALLER_TGZ} ${TEST_DIR}
