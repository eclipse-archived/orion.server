#!/bin/bash

#*******************************************************************************
# Copyright (c) 2011 IBM Corporation and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#     IBM Corporation - initial API and implementation
#*******************************************************************************

writableBuildRoot=/shared/eclipse/e4/orion
supportDir=/shared/eclipse/e4/build/e4

cd $writableBuildRoot

/usr/local/bin/git archive --format=tar --remote=/gitroot/e4/org.eclipse.orion.server.git master releng/org.eclipse.orion.releng/builder/scripts/masterBuild.sh | tar -xf -
mv releng/org.eclipse.orion.releng/builder/scripts/masterBuild.sh .
rm -rf releng

/bin/bash -l $writableBuildRoot/masterBuild.sh -root $writableBuildRoot -support $supportDir "$@" >$writableBuildRoot/logs/current.log 2>&1
