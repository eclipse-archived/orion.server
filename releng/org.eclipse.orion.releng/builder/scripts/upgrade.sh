# Copyright (c) 2010, 2013 IBM Corporation and others.
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

serverHome=/home/admin/current
workspaceHome=/home/data/nfs/serverworkspace

while [ $# -gt 0 ]
do
        case "$1" in
                "-archive")
                        newBuildArchive="$2"; shift;;

                 *) break;;      # terminate while loop
        esac
        shift
done

if [ "$newBuildArchive" ]
then
        echo "Upgrading server using $newBuildArchive"
else
        echo 'Usage: update.sh -archive <archive-name>'
        exit
fi

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
        echo Sleeping for 5 seconds to allow NFS mount to release
        sleep 5
fi

#delete old search index to save space
echo Deleting old search index
rm -fr $workspaceHome/.metadata/.plugins/org.eclipse.orion.server.core.search/
echo Deleting old tasks
rm -fr $workspaceHome/.metadata/.plugins/org.eclipse.orion.server.core/tasks/

#back-up the current server using a folder based on date
dateString=`date +%Y%m%d-%H%M`
oldBuildDir="eclipse-"${dateString}
echo Backing up current server to ${oldBuildDir}
mv $serverHome/eclipse $serverHome/$oldBuildDir


#unzip the new eclipse
pushd $serverHome
echo Unzipping $newBuildArchive
unzip -q $newBuildArchive
popd

#increase heap size in ini
echo Configuring server
sed -i 's/384m/5000m/g' $serverHome/eclipse/orion.ini
#remove console
sed -i '/^-console$/ d' $serverHome/eclipse/orion.ini
#set workspace location
sed -i 's/serverworkspace/\/home\/data\/nfs\/serverworkspace/g' $serverHome/eclipse/orion.ini
#specify Java 7 VM
sed -i '/^-vmargs$/i \
-vm \
$serverHome/jdk1.7.0_25/jre/bin/java' $serverHome/eclipse/orion.ini

#copy orion.conf file into server
cp $serverHome/orion.conf $serverHome/eclipse/orion.conf

#copy old $serverHome/server-status.json file into new server to preserve server messages
echo Copying old server status messages into new server
temp=$serverHome/$oldBuildDir/temp
mkdir $temp
mkdir $temp/web
pushd $temp
cp -f $serverHome/server-status.json $temp/web/server-status.json
# Replace the file inside the new UI jar.
echo Updating web/server-status.json in $serverHome/eclipse/plugins/org.eclipse.orion.client.ui_*.jar
zip -r $serverHome/eclipse/plugins/org.eclipse.orion.client.ui_*.jar web/server-status.json
popd
# End of server-status.json


#start new server
echo Starting server
pushd $serverHome/eclipse
ulimit -n 2000
./orion >> log.txt 2>&1 &

pid_eclipse="$!"
echo "Server started with pid $pid_eclipse"
echo $pid_eclipse > $serverHome/current.pid
popd

echo "Upgrade complete"
