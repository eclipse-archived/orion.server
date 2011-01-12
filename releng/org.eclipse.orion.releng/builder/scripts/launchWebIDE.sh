#*******************************************************************************
# Copyright (c) 2010 IBM Corporation and others.
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


testServer=/web/eclipse-web

while [ $# -gt 0 ]
do
	case "$1" in
		"-archive")
			testArchive="$2"; shift;;
					
		 *) break;;	 # terminate while loop
	esac
	shift
done


#take down the running eclipse 
echo Checking for running eclipse
if [ -e "$testServer/test.pid" ];
then
	runningPID=`cat $testServer/test.pid`
	echo Killing Eclipse instance $runningPID
	kill -15 $runningPID
	rm $testServer/test.pid
fi

#back-up the eclipse folder to "old"
if [ -d "$testServer/old" ];
then
	rm -rf $testServer/old
fi
mv $testServer/eclipse $testServer/old


#unzip the new eclipse 
pushd $testServer
echo Unzipping $testArchive
unzip -q $testArchive
popd

#remove -console from the .ini
sed -i '/^-console$/ d' $testServer/eclipse/eclipse.ini

#create symlink for the jre
pushd $testServer/eclipse
ln -s /web/builds/java/sun-160/jre jre

#start eclipse!
./eclipse > log.txt 2>&1 &
pid_eclipse="$!"
echo $pid_eclipse > $testServer/test.pid
popd
