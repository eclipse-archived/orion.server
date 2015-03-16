#!/bin/bash
#*******************************************************************************
# Copyright (c) 2010, 2015 IBM Corporation and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#     IBM Corporation - initial API and implementation
#*******************************************************************************
#

SERVERHOME=`dirname $0`
if [[ ${SERVERHOME} == *"orion.eclipse.org"* ]]
then
	HOST="orion.eclipse.org"
	PORT=9000
elif [[ ${SERVERHOME} == *"orionhub.org"* ]]
then
	HOST="orionhub.org"
	PORT=8000
else 
	echo "Unknown server home ${SERVERHOME}"
	exit
fi

WORKSPACEHOME=/localdata/${HOST}/serverworkspace/

while [ $# -gt 0 ]
do
        case "$1" in
                "-archive")
                        NEWBUILDARCHIVE="$2"; shift;;

                 *) break;;      # terminate while loop
        esac
        shift
done

if [ "${NEWBUILDARCHIVE}" ]
then
        echo "Upgrading server ${HOST} in ${SERVERHOME} running on port ${PORT} using ${NEWBUILDARCHIVE}"
else
        echo 'Usage: update.sh -archive <archive-name>'
        exit
fi

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
        echo Sleeping for 5 seconds to allow NFS mount to release
        sleep 5
fi

echo Deleting old tasks
rm -fr ${WORKSPACEHOME}/.metadata/.plugins/org.eclipse.orion.server.core/tasks/

#back-up the current server using a folder based on date
dateString=`date +%Y%m%d-%H%M`
oldBuildDir="eclipse-"${dateString}
echo Backing up current server to ${oldBuildDir}
mv ${SERVERHOME}/eclipse ${SERVERHOME}/$oldBuildDir


#unzip the new eclipse
pushd ${SERVERHOME}
echo Unzipping ${NEWBUILDARCHIVE}
unzip -q ${NEWBUILDARCHIVE}
popd

#increase heap size in ini
echo Configuring server
sed -i 's/384m/5000m/g' ${SERVERHOME}/eclipse/orion.ini
#remove console
sed -i '/^-console$/ d' ${SERVERHOME}/eclipse/orion.ini
#update port
sed -i "s/8080/${PORT}/" ${SERVERHOME}/eclipse/orion.ini
#set workspace location
sed -i "s/serverworkspace/\/localdata\/${HOST}\/serverworkspace/g" ${SERVERHOME}/eclipse/orion.ini
#set JRE location
sed -i "/^-vmargs$/i \
-vm \
${SERVERHOME}/current/jdk1.7.0_25/jre/bin/java" ${SERVERHOME}/eclipse/orion.ini

#copy orion.conf file into server
cp ${SERVERHOME}/orion.conf ${SERVERHOME}/eclipse/orion.conf

#copy old ${SERVERHOME}/server-status.json file into new server to preserve server messages
echo Copying old server status messages into new server
temp=${SERVERHOME}/$oldBuildDir/temp
mkdir $temp
mkdir $temp/web
pushd $temp
cp -f ${SERVERHOME}/server-status.json $temp/web/server-status.json
# Replace the file inside the new UI jar.
echo Updating web/server-status.json in ${SERVERHOME}/eclipse/plugins/org.eclipse.orion.client.ui_*.jar
zip -r ${SERVERHOME}/eclipse/plugins/org.eclipse.orion.client.ui_*.jar web/server-status.json
popd
# End of server-status.json

#start new server
echo Starting server
pushd ${SERVERHOME}/eclipse
ulimit -n 2000
ulimit -v 20000000
ulimit -c unlimited
./orion >> log.txt 2>&1 &

pid_eclipse="$!"
echo "Server started with pid $pid_eclipse"
popd
echo $pid_eclipse > ${SERVERHOME}/current.pid

echo "Upgrade complete"

