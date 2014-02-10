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

java=java
writableBuildRoot=/web/builds

while [ $# -gt 0 ]
do
	case "$1" in
		"-id")
			buildId="$2"; shift;;
			
		"-testConf")
			testConf="$2"; shift;;
			
		"-root")
			writableBuildRoot="$2"; shift;;
			
		"-display")
			DISPLAY="$2"; shift;;
			
		"-xvfb")
			xvfbCommand=-xvfb; shift;;
			
		"-server")
			serverPath="$2"; shift;;
			
		"-javaHome")
			javaHome="$2"; shift
			java=$javaHome/bin/java
			;;
			
		 *) break;;	 # terminate while loop
	esac
	shift
done

if [ ! -z "$xvfbCommand" ]; then
	xvfb=`which Xvfb`
	if [ "$?" -eq 1 ];
	then
	    echo "Xvfb not found."
	    exit 1
	fi
	
	$xvfb :63 -ac > /dev/null 2>&1 &	# launch virtual frame buffer into the background
	pid_xvfb="$!"			# take the process ID
	echo $pid_xvfb
	exit
fi
 
if [ ! -z "$serverPath" ]; then
	#remove -console from the .ini
	sed -i '/^-console$/ d' $serverPath/orion.ini
	$serverPath/orion -vm /shared/common/sun-jdk1.6.0_21_x64/jre/lib/amd64/server/libjvm.so > $serverPath/server.log 2>&1 &	
	pid_server="$!"			# take the process ID
	echo $pid_server
	exit
fi

killBrowser() {
	string=$1 ; shift
	
	browserPID=$( ps -ef | grep e4Build | grep $string | grep -v grep | awk '{print $2}' )
	for p in $browserPID; do
		kill $p
	done
}

testDir=$writableBuildRoot/tests/$buildId
if [[ ! -d $testDir ]]; 
then
mkdir $testDir
fi

browsers=(\
       firefox-23.0.1/firefox,firefox-23.0.1/firefox-bin)

export DISPLAY=:63		# set display to use that of the xvfb

#read the port number from the testConf file
port=`head -1 $testConf | sed 's_.*:\([0-9]*\)$_\1_'`

# run the tests
for entry in ${browsers[@]}; do
	OLD_IFS="$IFS"; IFS=","
	browser=( $entry )	
	IFS="$OLD_IFS"
	
	echo Running $testConf on port $port with browser ${browser[0]}
	$java -Dbrowser.timeout=120 -jar $testDir/../JsTestDriver.jar --config $testConf --port $port --browser /shared/common/${browser[0]} --tests all --testOutput $testDir
	killBrowser "${browser[1]}"
done
