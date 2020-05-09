# Copy this sample file to env-local.sh and customize it as needed.

export NAMESPACE=examples
export MAVEN_USERNAME=desdp

# Default set of charts to deploy.
CHARTS=${CHARTS:-\
stream-to-console-job \
}
