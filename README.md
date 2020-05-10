# Pravega Flink Tools

Pravega Flink Tools is a collection of Flink applications for working with Pravega streams.

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

### Instructions

1. Copy the file scripts/env-sample.sh to scripts/env-local.sh.
   This script will contain parameters for your environment.
   It is excluded from source control.
   Edit the file as follows.
   
   a. Enter your Kubernetes namespace that contains your Pravega stream (NAMESPACE).
      This is the name of your analytics project.

   b. Enter a name that identifies your environment (HELM_ENVIRONMENT).
      This will be used only to select the directory within values/environments.
      Recommended values are "test", "staging", "production".

   c. Enter your AWS S3 credentials (S3_ACCESS_KEY and S3_SECRET_KEY).

2. Copy the sample values file from values/environments/sample/flink-on-aws-s3.yaml to 
   values/environments/${HELM_ENVIRONMENT}/flink-on-aws-s3.yaml.
   Edit this file to use your AWS S3 bucket name (refer to instructions in the file).
   
3. Copy the sample job script scripts/samples/sample1-stream-to-aws-s3-job.sh to 
   scripts/jobs/my-stream-to-aws-s3-job.sh.
   You may name this file anything, but you must use alphanumeric characters and dashes only.
   
4. Copy the sample values file from values/environments/sample/sample1-stream-to-aws-s3-job.yaml to 
   values/environments/${HELM_ENVIRONMENT}/my-stream-to-aws-s3-job.yaml.
   The file name must match that of step 2.
   Edit this file to use your Pravega stream name, and AWS S3 bucket and path.

5. Launch the Flink job using Helm.
   ```
   scripts/samples/my-stream-to-aws-s3-job.sh
   ```

6. To copy additional streams, repeat steps 3 to 5.

7. To stop the job and delete all associated state:
   ```
   helm del my-stream-to-aws-s3-job -n ${NAMESPACE}
   ```

## References

- [Steaming File Sink](https://ci.apache.org/projects/flink/flink-docs-release-1.10/dev/connectors/streamfile_sink.html)
