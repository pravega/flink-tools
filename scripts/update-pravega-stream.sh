#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0

#set -ex

scope="edge"
stream_name="sensor-stream"
ret_policy_type=d
ret_policy_value=365
target_rate=1
scale_factor=1
min_segments=1
scale_policy_type="BY_RATE_IN_KBYTES_PER_SEC"

function usage() {
    echo "Usage: $0 [-s <scope name> -r <stream-name> -t <Retention Policy Type b for bytes d for days> -v <Retention Policy Value> -v <Retention Policy Value days or bytes> -e <Scaling factor target rate 0 or 1 and above> -f <Scaling factor> -m <Minimum Segments> -p Scaling Policy Type 1 for BY_RATE_IN_KBYTES_PER_SEC 2 for FIXED_NUM_SEGMENTS 3 for BY_RATE_IN_EVENTS_PER_SEC ] " 1>&2
    echo -e "\t -s Scope for which stream to be created."
    echo -e "\t -r Stream to be created"
    echo -e "\t -t Retention Policy Type"
    echo -e "\t -v Retention Policy Value"
    echo -e "\t -e Scaling factor target rate"
    echo -e "\t -f Scaling factor"
    echo -e "\t -m Minimum Segments"
    echo -e "\t -p Scaling Policy Type"
    exit 1
}

while getopts 's:r:t:v:e:f:m:p:h' opt; do
  case "${opt}" in
    s)  scope=$OPTARG ;;
    r)  stream_name=$OPTARG ;;
    t)  ret_policy_type=$OPTARG ;;
    v)  ret_policy_value=$OPTARG ;;
    e)  target_rate=$OPTARG ;;
    f)  scale_factor=$OPTARG ;;
    m)  min_segments=$OPTARG ;;
    h)   usage
           exit 1 ;;
    *)   usage
           exit 1 ;;
  esac
done
shift $((OPTIND -1))

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

_create_scope(){
  # Create scope
  CODE=$(curl -ksSL -w '%{http_code}' -o /dev/null -X POST -H "Content-Type: application/json" -u $un_pwd -d "{\"scopeName\":\"$scope\"}" ${pravega_url}/scopes)
  
  if [[ "$CODE" == "201" ]]; then
    # Server returned 2xx response
    echo "$scope created successfully"
  elif [[ "$CODE" = 409 ]]; then
    # Server returned 404, so compiling from source
    echo "$scope already exists"
  else
    echo "ERROR: server returned HTTP code $CODE"
    exit 1
  fi

}

_create_stream(){
  # Set Retention policy type	
  if [[ "$ret_policy_type" == "d" ]]; then
    type="LIMITED_DAYS"
  else
    type="LIMITED_SIZE_MB"
  fi
  # Set Scaling Policy Type
  if [[ "scale_policy_type=" == "1" ]]; then
    scale_type="BY_RATE_IN_KBYTES_PER_SEC"
  elif [[ "scale_policy_type=" == "2" ]]; then
    scale_type="FIXED_NUM_SEGMENTS"
  elif [[ "scale_policy_type=" == "3" ]]; then
    scale_type="BY_RATE_IN_EVENTS_PER_SEC"
  else
    scale_type="BY_RATE_IN_EVENTS_PER_SEC"	  
  fi
  
# Create stream
  CODE=$(curl -ksSL -w "%{http_code}" -o /dev/null -X POST -H "Content-Type: application/json" -u $un_pwd -d "{\"scopeName\":\"$scope\",\"streamName\":\"$stream_name\", \"scalingPolicy\":{\"type\":\"$scale_type\",\"targetRate\":\"$target_rate\",\"scaleFactor\":\"$scale_factor\",\"minSegments\":\"$min_segments\"}, \"retentionPolicy\" : {\"type\" : \"$type\", \"value\" : $ret_policy_value }}" ${pravega_url}/scopes/{$scope}/streams)
 echo "CREATE STREAM RESULT : $CODE "
  if [[ "$CODE" == "201" ]]; then
    # Server returned 2xx response
    echo "$stream_name created successfully"
  elif [[ "$CODE" == "404" ]]; then
    # Server returned 404, so compiling from source
    echo "$scope scope not found"
  elif [[ "$CODE" == "409" ]]; then
    echo "$stream_name already exists"
    exit 1
  elif [[ "$CODE" == "500" ]]; then
    echo "Internal server error while creating a stream $stream_name"
    exit 1
  fi
}


_get_pravega_cred
_get_pravega_controller_url
# _create_scope
_create_stream

exit 0

