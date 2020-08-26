
# How to test offline installation

Build the installer on your build host. This will require Internet access.
Then copy the installer to a temporary directory that the Docker container can read.
```shell script
user@build-host:~/flink-tools$
scripts/build-installer.sh && \
mkdir -p /tmp/dockertmp && \
cp build/installer/flink-tools.tgz /tmp/dockertmp/
```

Start an isolated Ubuntu container in Docker.
```shell script
user@build-host:~/flink-tools$
installer/test-docker-ubuntu-bash.sh
root@778971b7c4f3:/#
```

Install Java and other prerequisites.
This is the only step that will require Internet access from the isolated container.
```shell script
root@778971b7c4f3:/#
/work/installer/test-prereq.sh
```

Disable Internet access. This is simulated by disabling DNS resolution
for all hosts except the Maven host.
```shell script
root@778971b7c4f3:/#
echo > /etc/resolv.conf
echo "10.1.2.3 repo.examples.sdp.example.com" >> /etc/hosts
```

Extract the installer archive and publish the JAR to the Maven repo.
```shell script
root@778971b7c4f3:/#
/work/installer/test-publish-from-docker.sh
```
