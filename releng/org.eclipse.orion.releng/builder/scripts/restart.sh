#******************************************************************************
# Copyright (c) 2012, 2013 IBM Corporation and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#     IBM Corporation - initial API and implementation
#*******************************************************************************
#!/bin/bash
#
# A simple script to restart an Orion server

serverHome=/home/admin/current
workspaceHome=/opt/mnt/serverworkspace

#take down the running eclipse
echo Checking for running orion server
if [ -e "$serverHome/current.pid" ];
then
        runningPID=`cat $serverHome/current.pid`
        echo Killing Orion server instance $runningPID
        kill -15 $runningPID
        #orion executable immediately spawns java executable that also needs killing
        let "runningPID += 1"
        kill -15 $runningPID
        rm $serverHome/current.pid
fi

#delete old search index to save space
echo Deleting old search index
rm -fr $workspaceHome/.metadata/.plugins/org.eclipse.orion.server.core.search/
echo Deleting old tasks
rm -fr $workspaceHome/.metadata/.plugins/org.eclipse.orion.server.core/tasks/

#start new server
echo Starting server
pushd $serverHome/eclipse
ulimit -n 2000
./orion >> log.txt 2>&1 &

pid_eclipse="$!"
echo "Server started with pid $pid_eclipse"
echo $pid_eclipse > $serverHome/current.pid
popd

echo "Restart complete"
