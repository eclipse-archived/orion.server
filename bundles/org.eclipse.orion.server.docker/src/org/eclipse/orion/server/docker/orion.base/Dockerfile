################################################################################
# Copyright (c) 2013 IBM Corporation and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
# 
# Contributors:
#     IBM Corporation - initial API and implementation
#################################################################################
# Dockerfile for an Orion Node.js development image
# Wed Dec 11 15:18:14 EST 2013
FROM ubuntu:precise
MAINTAINER Anthony Hunter anthonyh@ca.ibm.com

# Configure a local user to interact with the volumes
RUN addgroup oriongroup
RUN adduser --home /OrionContent --shell /bin/bash --uid 1000 --gecos "Orion User,,," --ingroup oriongroup --disabled-password orionuser

# make sure the package repository is up to date and all updates installed
RUN echo "deb http://archive.ubuntu.com/ubuntu precise universe" >> /etc/apt/sources.list
RUN apt-get -y update
RUN apt-get -y dist-upgrade

# install node.js
RUN apt-get -y install python-software-properties python g++ make
RUN add-apt-repository -y ppa:chris-lea/node.js
RUN apt-get -y update
RUN apt-get -y install nodejs

# install grunt
RUN npm install -g grunt
RUN npm install -g grunt-cli

# install git client
RUN apt-get -y install git
