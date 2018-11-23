# Setting up a Docker server for use by the Orion server
Orion can take advantage of a [Docker](http://www.docker.io/) server to give an Orion user Unix shell access to the projects and files in the user's workspace.
There are several administrative steps the system administrator needs to perform to setup Orion with Docker.

# Server assumptions
The Docker daemon need to run as root, since `lxc-start` needs root privileges. As a result, it is currently common practice to have 
a separate dedicated server that only runs Docker. It is expected that we will run in this configuration.

# Starting the Docker server
The Orion server makes REST calls to the Docker server. So it is required that docker run in daemon mode binding to a TCP port. To do so, add
to the `/etc/init/docker.conf` file:
```
DOCKER_OPTS="-H tcp://localhost:4243 -H unix:///var/run/docker.sock"
```
# Secure access to the Docker server
Since there is now an open network port 4243, we need to make sure to control network access to docker. The system administrator needs to setup
the network and firewall such that only the Orion Server can connect to the Docker server via `tcp://localhost:4243`

# Update the docker URI in your Orion.conf
The `Orion.conf` configuration file needs an entry for the Docker server. An example:
```
orion.core.docker.uri=http://dockerhost:4243
```

# Make sure Docker can access the Orion serverworkspace
If the Orion workspace is located in `/opt/mnt/serverworkspace`, it is expected that Docker can access and mount files from this location.
The remote Docker server needs to have this folder mounted using NFS under `/opt/mnt/serverworkspace`. 

# Details for NFS access of the Orion serverworkspace from the Docker server
It should be noted that since the Docker
daemon runs as root, the `/opt/mnt/serverworkspace` will need to be accessable and reable by root. An way to specify in `/etc/exports` would be as follows:
```
/opt/mnt/serverworkspace	192.128.121.22(rw,sync,no_subtree_check,all_squash,anonuid=1000,anongid=1000)
```
In the case above, we specify `anonuid` and `anongid` so that all access is via the Orion user id and group id. To make sure that the Docker 
daemon can read and write within the Orion serverworkspace, a simple test if to  make sure `touch /opt/mnt/serverworkspace/newfile.txt` runs successfully
as root on the Docker server and the resulting file is visible as an update on the Orion server.

# Create a default Docker image named orion-base
Orion has provided a default Dockerfile that can be used to create an orion-base Docker image. Each user gets a container created using this image.
To create the image, run the command
```
sudo docker build -t="orion-base" .
```
The command needs to be run in the folder containing the Dockerfile

# Handle Orion user file access
It is expected that the Orion server process is not running as root. You may have an non administrative account on your server that you use to run Orion.
Docker needs to use a similar account, otherwise files Docker creates from the shell will be owned by root.
The orion-base Dockerfile creates an account:
```
# Configure a local user to interact with the volumes
RUN addgroup oriongroup
RUN adduser --home /OrionContent --shell /bin/bash --uid 1000 --gecos "Orion User,,," --ingroup oriongroup --disabled-password orionuser
```
The uid 1000 should match the uid on the Orion server.
For example, if Orion is running using the admin account and admin is uid 1000, then the Dockerfile also needs uid 1000. It follows that the NFS 
`anonuid` and `anongid` should also be using these same ids.
