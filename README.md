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

- **stream-to-file**: Continuously copy a Pravega stream to text files on S3, HDFS, or any other Flink-supported file system
- **stream-to-parquet-file**: Continuously copy a Pravega stream to Parquet files on S3, HDFS, or any other Flink-supported file system
- **stream-to-stream**: Continuously copy a Pravega stream to another Pravega stream, even on a different Pravega cluster
- **stream-to-console**: Continuously show the contents of a Pravega stream in a human-readable log file
- **sample-data-generator**: Continuously write synthetic data to Pravega for testing

Each job uses Flink checkpoints to provide exactly-once guarantees, ensuring that events
are never missed nor duplicated.
They automatically recover from failures and resume where they left off.
They can use parallelism for high-volume streams with multiple segments.

To learn more about Pravega, visit http://pravega.io

## Prerequisites

- Java JDK 8.x.
  On Ubuntu, this can be installed with:
  ```shell script
  sudo apt-get install openjdk-8-jdk
  ```

- A [Pravega](http://pravega.io) installation

- The deployment scripts in this project are designed to work with
  Dell EMC Streaming Data Platform (SDP).
  These Flink tools may also be used in other Flink installations,
  including open-source, although the exact
  deployment methods depend on your environment and are not documented here.

## Stream-to-File: Continuously copying a Pravega stream to text files

### Overview

This Flink job will continuously copy a Pravega stream to a set of text files 
on S3, HDFS, NFS, or any other Flink-supported file system.
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
When written to text files, each event will be followed by a new line.
For binary events, you will need to customize the Flink job with the appropriate serialization classes.

Flink offers many options for customizing the behavior when writing files.
Refer to [Steaming File Sink](https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/connectors/streamfile_sink.html)
for details.

### Deploy to SDP using the SDP UI

Below shows how to deploy this Flink job using the SDP UI.
If you would rather use a more automated deployment method, skip to the next section.

1. Build the JAR file.
   ```shell script
   ./gradlew clean shadowJar
   ```

2. Upload the artifact:
   - group: io.pravega
   - artifact: flink-tools
   - version: 0.2.0
   - file: flink-tools/build/libs/pravega-flink-tools-0.2.0.jar

3. Create Flink Cluster.
   - Name: stream-to-file
   - Flink Image: 1.10.0-2.12-hadoop2.8.3 (1.10.0)
   - Replicas: 1
   - Task Slots: 1
   
4. Create New App.
   - Name: stream-to-file
   - Artifact: io.pravega:fliink-tools:0.2.0
   - Main Class: io.pravega.flinktools.StreamToFileJob
   - Cluster Selectors: name: stream-to-file
   - Parallelism: 1
   - Flink Version: 1.10.0
   - Add Parameters:
     - output: hdfs://hadoop-hadoop-hdfs-nn.examples.svc.cluster.local:9000/tmp/sample1
     - scope: examples (This should match your SDP project name.)
   - Add Stream:
     - input-stream: sample1

### Deploy to SDP using Helm

1. If you will be using HDFS, you must install the Flink cluster image that includes the Hadoop client library.
   ```shell script
   scripts/flink-image-install.sh
   ```

2. Copy the file `scripts/env-sample.sh` to `scripts/env-local.sh`
   or any other destination.
   This script will contain parameters for your environment.
   Edit the file as follows.
   
   a. Enter your Kubernetes namespace that contains your Pravega stream (NAMESPACE).
      This is the name of your analytics project.
      
   Example file `scripts/env-local.sh`:
   ```shell script
   export NAMESPACE=examples
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

6. To copy additional streams, repeat steps 3 to 5.

7. To stop the job and delete all associated state:
   ```shell script
   helm del my-stream-to-file-job -n ${NAMESPACE}
   ```

## Stream-to-Parquet-File: Continuously copying a Pravega stream to Parquet files

### Overview

This Flink job will continuously copy a Paravega stream to a set of 
[Apache Parquet](https://en.wikipedia.org/wiki/Apache_Parquet) files 
on S3, HDFS, NFS, or any other Flink-supported file system.

Apache Parquet is a column-oriented data storage format of the Apache Hadoop ecosystem.
It provides efficient data compression and encoding schemes with enhanced performance to handle complex data in bulk.

This Flink job uses Flink checkpoints to provide exactly-once guarantees, ensuring that events
are never missed nor duplicated.
It automatically recovers from failures and resumes where it left off.
It can use parallelism for high-volume streams with multiple segments.

Input events must be in JSON format.
To ensure that JSON events can be reliable converted to Parquet, you must specify the
[Apache Avro schema](http://avro.apache.org/docs/1.8.2/spec.html) that corresponds to the JSON events.

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

### Flattening records

When writing to Parquet files, the input JSON events can be optionally transformed
using a flatten operation.

When enabled, input JSON events in this format

```json
{
"RemoteAddr":"gw3001",
"Timestamp":[1597883617606,1597883617616,1597883617626],
"X":[0.0,0.1,0.2],
"Y":[0.0,-0.1,-0.2],
"Z":[9.9,9.8,9.7]
}
```

will be written with this structure.

| RemoteAddr | Timestamp     | X   | Y    | Z   |
| ---------- | ------------- | --- | ---- | --- |
| gw3001     | 1597883617606 | 0.0 |  0.0 | 9.9 |
| gw3001     | 1597883617616 | 0.1 | -0.1 | 9.8 |
| gw3001     | 1597883617626 | 0.2 | -0.2 | 9.7 |

All fields containing arrays must have the same number of elements.
All non-array fields will be duplicated on each record.

This flatten transformation can be enabled by setting the `flatten` parameter to `true`.

### Deploy to SDP

Refer to the method described in the Stream-to-File section.
Use the script `scripts/jobs/stream-to-parquet-file-job.sh` and a values file similar to 
`values/samples/sample1-stream-to-parquet-hdfs-job.yaml`.

### How to view Parquet files

If the Parquet file is located on a standard Linux file system (including NFS),
you can use a command similar to the following to view the content.

```shell script
scripts/parquet-tools.sh cat /tmp/sample1-parquet/2020-08-19--03/part-0-887
``` 

If the Parquet file is located on an HDFS cluster in Kubernetes,
you can use commands similar to the following to view the content.

```shell script
scripts/hadoop-bash.sh
root@hadoop-8c428aa0-76c0-4f42-8bea-2fc1e8300f78:~#
wget https://repo1.maven.org/maven2/org/apache/parquet/parquet-tools/1.11.1/parquet-tools-1.11.1.jar
hadoop jar parquet-tools-1.11.1.jar cat hdfs://hadoop-hadoop-hdfs-nn.examples.svc.cluster.local:9000/tmp/sample1-parquet/2020-08-19--03/part-0-887
```

## Writing to an NFS volume

Use this procedure to configure the Flink Stream to File job to write to any Kubernetes Persistent Volume, such as a remote NFS volume.

1. Create a Persistent Volume and a Persistent Volume Claim.

   a. Edit `values/nfs-samples/external-nfs-pv.yaml` and
      `values/nfs-samples/external-nfs-pv.yaml`.
      
   b. Create objects.
      ```
      export NAMESPACE=examples
      kubectl create -f values/nfs-samples/external-nfs-pv.yaml
      kubectl create -f values/nfs-samples/external-nfs-pvc.yaml -n ${NAMESPACE}
      ```

2. Configure extra NFS mount in `values/samples/sample1-stream-to-nfs-job.yaml`
   ```
   jobManager:
     volumeMounts:
       - mountPath: /mnt/nfs
         name: extra-volume
   taskManager:
     volumeMounts:
       - mountPath: /mnt/examples-sample
         name: extra-volume
   ```

4. Configure mount path in values/samples/sample1-stream-to-nfs-job.yaml

   ```
   output: "/mnt/nfs"
   ```

5. Deploy Flink job for NFS.

   ```
   scripts/jobs/stream-to-file-job.sh values/samples/sample1-stream-to-nfs-job.yaml
   ```

## Stream-to-Stream: Continuously copying a Pravega stream to another Pravega stream

### Overview

This Flink job will continuously copy a Pravega stream to another Pravega stream.
It uses Flink checkpoints to provide exactly-once guarantees, ensuring that events
are never missed nor duplicated.
It automatically recovers from failures and resumes where it left off.
It can use parallelism for high-volume streams with multiple segments.

### Deploy to SDP

Refer to the method described in the Stream-to-File section.

## Sample data generator

### Overview

This Flink job continuously generates synthetic JSON events and writes them to a Pravega stream.
It can be used for testing the other applications.
The event size and event rate can be specified as parameters.

Events are in the format shown below. 
```json
{"sensorId":0,"eventNumber":42,"timestamp":1591294714504,"timestampStr":"2020-06-04 18:18:34.504","data":"xxxxx..."}
```

### Deploy to SDP using the SDP UI

Below shows how to deploy this Flink job using the SDP UI.

1. Build the JAR file.
   ```shell script
   ./gradlew clean shadowJar
   ```

2. Upload the artifact:
   - group: io.pravega
   - artifact: flink-tools
   - version: 0.2.0
   - file: flink-tools/build/libs/pravega-flink-tools-0.2.0.jar

3. Create Flink Cluster.
   - Name: sample-data-generator-job
   - Flink Image: 1.10.0-2.12 (1.10.0)
   - Replicas: 1
   - Task Slots: 1
   
4. Create New App.
   - Name: sample-data-generator-job
   - Artifact: io.pravega:fliink-tools:0.2.0
   - Main Class: io.pravega.flinktools.SampleDataGeneratorJob
   - Cluster Selectors: name: sample-data-generator-job
   - Parallelism: 1
   - Flink Version: 1.10.0
   - Add Parameters:
     - scope: examples (This should match your SDP project name.)
   - Add Stream:
     - output-stream: sample1 (Select the stream to write to.)

### Deploy to SDP using Helm

Refer to the method described in the Stream-to-file section. 

## Deduplication of events

Although Pravega provides exactly-once semantics, some event sources may not fully utilize
these features and these may produce duplicate events in some failure cases.
The jobs in Pravega Flink Tools can use the stateful functionality of Flink to
remove duplicates when writing to the output stream or file.

This functionality requires the following:

- The events must be JSON objects such as
  `{"key1": 1, "counter": 10, "value1": 100}`.
  
- The events must have one or more key columns, such as "key1" in the above example.
  Each key may be any JSON data type, including strings, numbers, arrays, and objects. 

- The event must have a counter column, such as "counter" in the above example.
  It must be a numeric data type. A 64-bit long integer is recommended.
  
- For each unique key, the counter *must* be ascending.
  If the counter decreases or repeats, the event is considered a duplicate and it is logged and dropped.
  
Beware of using a timestamp for the counter. 
A clock going backwards due to an NTP correction may result in large amounts of data being dropped.
Events produced faster than the clock resolution may also be dropped.

## Enabling deduplication

1. Set the parameter `keyFieldNames` to a list of JSON field names for the keys, separated by commas.

2. Set the parameter `counterFieldName` to the JSON field name for the counter.

If either of these parameters is empty, deduplication will be disabled, which is the default behavior.

When deduplication is enabled, any errors when parsing the JSON or accessing the keys or counter
will be logged and the event will be discarded.

### Deploying to SDP using Helm without Internet access

This procedure can be used to automate the deployment of the Flink jobs on a system without Internet access.
The first section must be executed on a build host that has Internet access.
This will download all dependencies and create a single archive that can be copied to an offline SDP system.
This archive can then be used to script the deployment of the Flink jobs.

1. On the build host, build the installation archive.
   ```shell script
   user@build-host:~/flink-tools$
   scripts/build-installer.sh
   ```
   This will create the installation archive `build/installer/flink-tools.tgz`.
   
2. Copy the installation archive to the SDP system,
   then extract it.
   ```shell script
   user@sdp-host:~/desdp$
   tar -xzf flink-tools.tgz
   cd flink-tools
   ```
   
3. The only prerequisite on the SDP system is Java 8.x.
   On Ubuntu, this can be installed with:
   ```shell script
   sudo apt-get install openjdk-8-jdk
   ```
   
4.  Continue with the procedure in the section [Deploy to SDP using Helm](#deploy-to-sdp-using-helm).

## References

- [Steaming File Sink](https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/connectors/streamfile_sink.html)
