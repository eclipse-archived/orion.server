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
			
		"-javaHome")
			javaHome="$2"; shift;;
			
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
 
testDir=$writableBuildRoot/tests/$buildId
if [[ ! -d $testDir ]]; 
then
mkdir $testDir
fi

firefox=/shared/common/firefox-3.6.13/firefox
if [[ ! -e "$firefox" ]]; then
firefox=`which firefox`
fi

firefox4=/shared/common/firefox-4.0b11/firefox
opera=/shared/common/opera-11.01-1190/bin/opera

chrome=/shared/common/chrome-8.0.552.237/google-chrome
if [[ ! -e "$chrome" ]]; then
	chrome=`which chrome`
fi

export DISPLAY=:63		# set display to use that of the xvfb

#read the port number from the testConf file
port=`head -1 $testConf | sed 's_.*:\([0-9]*\)$_\1_'`

echo Running $testConf on port $port
# run the tests
if [ ! -z "$javaHome" ]; then
	$javaHome/bin/java -Dbrowser.timeout=120 -jar $testDir/../JsTestDriver.jar --config $testConf --port $port --browser $firefox,$chrome --tests all --testOutput $testDir
	$javaHome/bin/java -Dbrowser.timeout=120 -jar $testDir/../JsTestDriver.jar --config $testConf --port $port --browser $firefox4,$opera --tests all --testOutput $testDir
else
	java -Dbrowser.timeout=120 -jar $testDir/../JsTestDriver.jar --config $testConf --port $port --browser $firefox,$chrome --tests all --testOutput $testDir
	java -Dbrowser.timeout=120 -jar $testDir/../JsTestDriver.jar --config $testConf --port $port --browser $opera,$firefox4 --tests all --testOutput $testDir
fi

