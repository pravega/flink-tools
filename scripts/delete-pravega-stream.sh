#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0

scope=""
stream_name=""

function usage() {
    echo "Usage: $0 [-s <scope name> -r <stream-name> ] " 1>&2
    echo -e "\t -s Scope for which stream is to be deleted."
    echo -e "\t -r Stream to be deleted"
    exit 1
}

while getopts 's:r:h' opt; do
  case "${opt}" in
    s)  scope=$OPTARG ;;
    r)  stream_name=$OPTARG ;;
    h)   usage
           exit 1 ;;
    *)   usage
           exit 1 ;;
  esac
done
shift $((OPTIND -1))

if [[ -z ${stream_name} || -z ${scope} ]]; then
    usage
fi


# find the pravega controller authentication details
_get_pravega_cred(){
   un_pwd="`kubectl get pravegaCluster -n nautilus-pravega -o yaml | grep -i pravega.client.auth.token | awk '{ print $2; }' | base64 -d`"
   echo "Pravega Controller and Password: $un_pwd"
}

# find the pravega controller URL
_get_pravega_controller_url(){
  # TLS Enabled
  pravega_url=`kubectl get ingress -n nautilus-pravega pravega-controller-api | grep pravega-controller | awk '{ print $2; }'`
  echo "Pravega URL : ${pravega_url}"
  if [[ -z "$pravega_url"   ]]; then
      echo "Not Found TLS enabled Pravega URL ${pravega_url}"
      # TLS Disabled
      pravega_url=(`kubectl get -n nautilus-pravega svc nautilus-pravega-controller -o go-template=$'{{index .metadata.annotations "external-dns.alpha.kubernetes.io/hostname"}}\n'`)
      pravega_url=http://${pravega_url}:9090/v1
      echo "Found TLS disabled Pravega URL ${pravega_url}"
  else
     pravega_url=https://${pravega_url}/v1
  fi

   echo "Final Pravega URL : ${pravega_url}"
}

_seal_stream(){
# Create stream
  CODE=$(curl -ksSL -w "%{http_code}" -o /dev/null -X PUT -H "Content-Type: application/json" -u $un_pwd -d "{\"streamState\":\"SEALED\"}" ${pravega_url}/scopes/${scope}/streams/${stream_name}/state)
 echo "CREATE STREAM RESULT : $CODE "
  if [[ "$CODE" == "200" ]]; then
    # Server returned 2xx response
    echo "$stream_name updated successfully"
  elif [[ "$CODE" == "404" ]]; then
    # Server returned 404, so compiling from source
    echo "$scope scope $stream_name stream not found"
  elif [[ "$CODE" == "500" ]]; then
    echo "Internal server error while creating a stream $stream_name"
    exit 1
  fi
}

_delete_stream(){
# Delete stream
  echo "(curl -ksSL -w \"%{http_code}\" -o /dev/null -X DELETE -H \"Content-Type: application/json\" -u $un_pwd ${pravega_url}/scopes/${scope}/streams/${stream_name})"
  CODE=$(curl -ksSL -w "%{http_code}" -o /dev/null -X DELETE -H "Content-Type: application/json" -u $un_pwd ${pravega_url}/scopes/${scope}/streams/${stream_name})
 echo "DELETE STREAM RESULT : $CODE "
  if [[ "$CODE" == "204" ]]; then
    # Server returned 2xx response
    echo "$stream_name deleted successfully"
  elif [[ "$CODE" == "404" ]]; then
    # Server returned 404, so compiling from source
    echo "$scope/$stream_name scope/stream not found"
  elif [[ "$CODE" == "412" ]]; then
    echo "$stream_name cannot be deleted since it is not sealed"
    exit 1
  elif [[ "$CODE" == "500" ]]; then
    echo "Internal server error while deleting a stream $stream_name"
    exit 1
  fi
}


_get_pravega_cred
_get_pravega_controller_url
_seal_stream
_delete_stream

exit 0
