# Setting up Docker for use by the Orion server
There are several administrative steps the admin needs to do so that a user can access the Docker shell

# Update the docker URI in your Orion.conf
The Orion.conf configuration file needs an entry for the docker server. An example:
```
orion.core.docker.uri=http://localhost:4243
```

# Make sure Docker can access the Orion serverworkspace
If the Orion workspace is located in /opt/mnt/serverworkspace, it is expected that Docker can access and mount files from this location.
If Docker is a remote server, it needs to have this folder mounted using NFS under /opt/mnt/serverworkspace

# Create a default Docker image named orion.base
Orion has provided a default Dockerfile that can be used to create an orion.base Docker image. Each user gets a container created using this image.
To create the image, run the command
```
sudo docker build -t="orion.base" .
```
The command needs to be run in the folder containing the Dockerfile

# Handle Orion user file access
It is expected that the Orion server is not running as root. You may have an non administrative account on your server that you use to run Orion.
Docker needs to use a similar account, otherwise files Docker creates from the shell will be owned by root.
The orion.base Dockerfile creates an account:
```
# Configure a local user to interact with the volumes
RUN addgroup oriongroup
RUN adduser --home /OrionContent --shell /bin/bash --uid 1000 --gecos "Orion User,,," --ingroup oriongroup --disabled-password orionuser
```
The uid 1000 should match the uid on the Orion server
For example, if Orion is running using the admin account and admin is uid 113, then the Dockerfile also needs uid 113 
