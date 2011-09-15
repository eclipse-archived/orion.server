#******************************************************************************
# Copyright (c) 2010, 2011 IBM Corporation and others.
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
        rm $serverHome/current.pid
fi

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

#copy server workspace to new install
echo Copying server workspace
cp -r $serverHome/$oldBuildDir/serverworkspace $serverHome/eclipse/

#increase heap size in ini
sed -i 's/384m/800m/g' $serverHome/eclipse/orion.ini

#copy orion.conf file into server
cp $serverHome/orion.conf $serverHome/eclipse/orion.conf

#start new server
pushd $serverHome/eclipse
nohup ./orion &

pid_eclipse="$!"
echo $pid_eclipse > $serverHome/current.pid
popd
