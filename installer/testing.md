
# How to test offline installation

Build the installer on your build host.
This will require Internet access.
```bash
user@build-host:~/flink-tools
scripts/build-installer.sh
```

Copy the installer to a temporary directory that the Docker container can read.
```bash
user@build-host:~/flink-tools
mkdir -p /tmp/dockertmp
cp build/installer/flink-tools.tgz /tmp/dockertmp/
```

Start an isolated Ubuntu container in Docker.
```bash
user@build-host:~/flink-tools$
installer/test-docker-ubuntu-bash.sh
root@778971b7c4f3:/#
```

Install Java and other prerequisites.
This is the only step that will require Internet access from the isolated container.
```bash
root@778971b7c4f3:/# /work/test-prereq.sh
```

Extract the installer archive and publish the JAR to the Maven repo.
```bash
root@778971b7c4f3:/# /work/test-publish-from-docker.sh
```
