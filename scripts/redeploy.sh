#!/usr/bin/env bash
set -ex
ROOT_DIR=$(dirname $0)/..
source ${ROOT_DIR}/scripts/env-local.sh

if [[ "${SKIP_UNINSTALL}" != "1" ]]; then
    ${ROOT_DIR}/scripts/uninstall.sh
fi

if [[ "${SKIP_PUBLISH}" != "1" ]]; then
    ${ROOT_DIR}/scripts/publish.sh
fi

if [[ "${SKIP_UNINSTALL}" != "1" ]]; then
  for chart in ${CHARTS} ; do
      kubectl wait --for=delete --timeout=900s FlinkCluster/${chart} -n ${NAMESPACE} || true
  done
fi

${ROOT_DIR}/scripts/deploy.sh
