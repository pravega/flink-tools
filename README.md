<!--
Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
-->
# Pravega Flink Tools

Pravega Flink Tools is a collection of Apache Flink applications for working with Pravega streams.

It provides the following Flink jobs:

- **stream-to-file**: Continuously copy a Pravega stream to files on S3, HDFS, or any other Flink-supported file system
- **stream-to-stream**: Continuously copy a Pravega stream to another Pravega stream, even on a different Pravega cluster
- **stream-to-console**: Continuously show the contents of a Pravega stream in a human-readable log file
- **sample-data-generator**: Continuously write synthetic data to Pravega for testing

Each job uses Flink checkpoints to provide exactly-once guarantees, ensuring that events
are never missed nor duplicated.
They automatically recover from failures and resume where they left off.
They can use parallelism for high-volume streams with multiple segments.

To learn more about Pravega, visit http://pravega.io

## Prerequisites

- Java 8.x

- A [Pravega](http://pravega.io) installation

- The deployment scripts in this project are designed to work with
  Dell EMC Streaming Data Platform (SDP).
  These Flink tools may also be used in other Flink installations,
  including open-source, although the exact
  deployment methods depend on your environment and are not documented here.

## Stream-to-file: Continuously copying a Pravega stream to files

### Overview

This Flink job will continuously copy a Pravega stream to a set of files 
on S3, HDFS, or any other Flink-supported file system.
It uses Flink checkpoints to provide exactly-once guarantees, ensuring that events
are never missed nor duplicated.
It automatically recovers from failures and resumes where it left off.
It can use parallelism for high-volume streams with multiple segments.

By default, it writes a new file every 1 minute. 
Files are written using the following directory structure.
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

1. If you will be using HDFS, you must install the Flink cluster image that includes the Hadoop client library.
   ```shell script
   scripts/flink-image-install.sh
   ```

2. Copy the file `scripts/env-sample.sh` to `scripts/env-local.sh`.
   This script will contain parameters for your environment.
   Edit the file as follows.
   
   a. Enter your Kubernetes namespace that contains your Pravega stream (NAMESPACE).
      This is the name of your analytics project.

   Example file `scripts/env-local.sh`:
   ```shell script
   export NAMESPACE=examples   
   export MAVEN_USERNAME=desdp   
   ```

3. Copy the sample values file from `values/samples/sample1-stream-to-aws-s3-job.yaml` or
   `values/samples/sample1-stream-to-hdfs-job.yaml` to
   `values/local/my-stream-to-file-job.yaml` or any other destination.
   You may name this file anything, but you must use alphanumeric characters and dashes only.

4. Edit this file to use your Pravega stream name and output directory.
   You can also change the checkpoint interval, which is how often events
   will be written to the files.
   
5. Launch the Flink job using Helm.
   ```shell script
   scripts/jobs/stream-to-file-job.sh values/local/my-stream-to-file-job.yaml
   ```

6. To copy additional streams, repeat steps 2 to 4.

7. To stop the job and delete all associated state:
   ```
   helm del my-stream-to-file-job -n ${NAMESPACE}
   ```

## Stream-to-Stream: Continuously copying a Pravega stream to another Pravega stream

### Overview

This Flink job will continuously copy a Pravega stream to another Pravega stream.
It uses Flink checkpoints to provide exactly-once guarantees, ensuring that events
are never missed nor duplicated.
It automatically recovers from failures and resumes where it left off.
It can use parallelism for high-volume streams with multiple segments.

### Deploy to SDP using Helm

Refer to the method described in the Stream-to-file section. 

## Sample data generator

### Overview

This Flink job continuously generates synthetic JSON events and writes them to a Pravega stream.
It can be used for testing the other application.

### Deploy to SDP using Helm

Refer to the method described in the Stream-to-file section. 

### Deploy to SDP using the SDP UI

Below shows how to deploy this Flink job using the SDP UI.

1. Build the JAR file.
   ```shell script
   ./gradlew clean shadowJar
   ```

2. Upload the artifact:
   - group: io.pravega
   - artifact: flink-tools
   - version: 0.1.0
   - file: flink-tools/build/libs/pravega-flink-tools-0.1.0.jar
   
3. Create New App.
   - Main Class: io.pravega.flinktools.SampleDataGeneratorJob
   - Flink Version: 1.10.0
   - Add Parameters:
     - scope: examples (This should match your SDP project name.)
   - Add Stream:
     - output-stream: sample1 (Select the stream to write to.)
        
4. Create Flink Cluster.
   - Flink Image: 1.10.0-2.12 (1.10.0)
   - Replicas: 1
   - Task Slots: 1     

## References

- [Steaming File Sink](https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/connectors/streamfile_sink.html)
