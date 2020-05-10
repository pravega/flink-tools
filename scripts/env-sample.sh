# Copy this sample file to env-local.sh and customize it as needed.

export NAMESPACE=examples
export MAVEN_USERNAME=desdp

export S3_ENDPOINT=s3.amazonaws.com
export S3_BUCKET=my-bucket-name
export S3_ACCESS_KEY=xxx
export S3_SECRET_KEY=xxx

# Default set of charts to deploy.
CHARTS=${CHARTS:-\
stream-to-console-job \
}
