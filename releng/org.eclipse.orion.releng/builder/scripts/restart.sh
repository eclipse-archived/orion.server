#!/bin/bash
#******************************************************************************
# Copyright (c) 2012, 2015 IBM Corporation and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#     IBM Corporation - initial API and implementation
#*******************************************************************************

SERVERHOME=`dirname $0`
if [[ ${SERVERHOME} == *"orion.eclipse.org"* ]]
then
	HOST="orion.eclipse.org"
elif [[ ${SERVERHOME} == *"orionhub.org"* ]]
then
	HOST="orionhub.org"
else 
	echo "Unknown server home ${SERVERHOME}"
	exit
fi

WORKSPACEHOME=/localdata/${HOST}/serverworkspace/

#take down the running eclipse
echo Checking for running orion server
if [ -e "${SERVERHOME}/current.pid" ];
then
        runningPID=`cat ${SERVERHOME}/current.pid`
        echo Killing Orion server instance $runningPID
        kill -15 $runningPID
        #orion executable immediately spawns java executable that also needs killing
        let "runningPID += 1"
        kill -15 $runningPID
        rm ${SERVERHOME}/current.pid
fi

echo Deleting old tasks
rm -fr ${WORKSPACEHOME}/.metadata/.plugins/org.eclipse.orion.server.core/tasks/

#start new server
echo Starting server
pushd ${SERVERHOME}/eclipse
ulimit -n 2000
ulimit -v 20000000
ulimit -c unlimited
./orion >> log.txt 2>&1 &

pid_eclipse="$!"
echo "Server started with pid $pid_eclipse"
echo $pid_eclipse > ${SERVERHOME}/current.pid
popd

echo "Restart complete"
