<!--
Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
-->
# Pravega Flink Tools

Pravega Flink Tools is a collection of Flink applications for working with Pravega streams.

To learn more about Pravega, visit http://pravega.io

## Prerequisites

- Java 8.x

- A [Pravega](http://pravega.io) installation

- The deployment scripts in this project are designed to work with
  Dell EMC Streaming Data Platform (SDP).
  These Flink tools may also be used in other Flink installations,
  including open-source, although the exact
  deployment method depends on your environment and is not documented here.

## Continuously copying a Pravega stream to AWS S3

### Overview

This Flink job will continuously copy a Pravega stream to a set of objects on AWS S3.
It uses Flink checkpoints to provide exactly-once guarantees, ensuring that events
are never missed nor duplicated.
It automatically recovers from failures and resumes where it left off.
It can use parallelism for high-volume streams with multiple segments.

By default, it writes a new file every 1 minute. 
Objects are written using the following directory structure.
```
sample1/2020-05-10--18/part-0-0
sample1/2020-05-10--18/part-0-1
sample1/2020-05-10--18/part-0-2
...
sample1/2020-05-10--18/part-0-59
sample1/2020-05-10--19/part-0-60
sample1/2020-05-10--19/part-0-61
```

For simplicity, the current implementation assumes that events are UTF-8 strings such as CSV or JSON.
When written to files, each event will be followed by a new line.
For binary events, you will need to customize the Flink job with the appropriate serialization classes.

Flink offers many options for customizing the behavior when writing files.
Refer to [Steaming File Sink](https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/connectors/streamfile_sink.html)
for details.

### Deploy to SDP using Helm

1. Copy the file `scripts/env-sample.sh` to `scripts/env-local.sh`.
   This script will contain parameters for your environment.
   It is excluded from source control.
   Edit the file as follows.
   
   a. Enter your Kubernetes namespace that contains your Pravega stream (NAMESPACE).
      This is the name of your analytics project.

   b. Enter a name that identifies your environment (HELM_ENVIRONMENT).
      This will be used only to select the directory within values/environments.
      Recommended values are "test", "staging", "production".

   c. Enter your AWS S3 credentials (S3_ACCESS_KEY and S3_SECRET_KEY).
   
   Example file `scripts/env-local.sh`:
   ```shell script
   export NAMESPACE=examples   
   export MAVEN_USERNAME=desdp   
   export HELM_ENVIRONMENT=sample   
   export S3_ACCESS_KEY=xxx
   export S3_SECRET_KEY=xxx
   ```

2. Copy the sample values file from `values/environments/sample/flink-on-aws-s3.yaml` to 
   `values/environments/${HELM_ENVIRONMENT}/flink-on-aws-s3.yaml`.
   Edit this file to use your AWS S3 bucket name (refer to instructions in the file).
   
3. Copy the sample job script `scripts/samples/sample1-stream-to-aws-s3-job.sh` to 
   `scripts/jobs/my-stream-to-aws-s3-job.sh`.
   You may name this file anything, but you must use alphanumeric characters and dashes only.
   
   Example file `scripts/jobs/my-stream-to-aws-s3-job.sh` (some sections omitted for clarity):
   ```shell script
    helm upgrade --install \
        ${RELEASE_NAME} \
        --namespace ${NAMESPACE} \
        ${ROOT_DIR}/charts/flink-tools \
        -f ${ROOT_DIR}/charts/flink-tools/values.yaml \
        -f ${ROOT_DIR}/values/job-defaults/stream-to-file-job.yaml \
        -f ${ROOT_DIR}/values/environments/${HELM_ENVIRONMENT}/flink-on-aws-s3.yaml \
        -f ${ROOT_DIR}/values/environments/${HELM_ENVIRONMENT}/${RELEASE_NAME}.yaml \
        --set clusterConfiguration."s3\.access-key"=${S3_ACCESS_KEY} \
        --set clusterConfiguration."s3\.secret-key"=${S3_SECRET_KEY}
   ```
   
4. Copy the sample values file from `values/environments/sample/sample1-stream-to-aws-s3-job.yaml` to 
   `values/environments/${HELM_ENVIRONMENT}/my-stream-to-aws-s3-job.yaml`.
   The file name must match that of step 2.
   Edit this file to use your Pravega stream name, and AWS S3 bucket and path.
   You can also change the checkpoint interval, which is how often events
   will be written to the S3 bucket.
   
   Example file `values/environments/sample/my-stream-to-aws-s3-job.yaml`:
   ```yaml
    appParameters:
      checkpointIntervalMs: "60000"
      input-stream: "my-stream"
      output: "s3a://my-bucket/my-stream"
   ```

5. Launch the Flink job using Helm.
   ```shell script
   scripts/samples/my-stream-to-aws-s3-job.sh
   ```

6. To copy additional streams, repeat steps 3 to 5.

7. To stop the job and delete all associated state:
   ```
   helm del my-stream-to-aws-s3-job -n ${NAMESPACE}
   ```

## Sample data generator

### Deploy to SDP using the SDP UI

1. Upload the artifact:
   group: io.pravega
   artifact: flink-tools
   version: 0.1.0
   
2. Create New App.
   Main Class: io.pravega.flinktools.SampleDataGeneratorJob
   Flink Version: 1.10.0
   Add Parameters:
        scope: examples (This should match your SDP project name.)
   Add Stream:
        output-stream: sample1 (Select the stream to write to.)
        
3. Create Flink Cluster.
   Flink Image: 1.10.0-2.12 (1.10.0)
   Replicas: 1
   Task Slots: 1     

## References

- [Steaming File Sink](https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/connectors/streamfile_sink.html)
