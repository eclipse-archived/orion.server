#!/bin/bash

#*******************************************************************************
# Copyright (c) 2010, 2012 IBM Corporation and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#     IBM Corporation - initial API and implementation
#*******************************************************************************

PATH=$PATH:/usr/local/bin
export PATH

#default values, overridden by command line
writableBuildRoot=/shared/eclipse/e4/orion
supportDir=$writableBuildRoot/support
builderDir=$supportDir/org.eclipse.orion.releng
basebuilderBranch=R3_8
publish=""
user=johna
resultsEmail=orion-releng@eclipse.org

buildType=I
date=$(date +%Y%m%d)
time=$(date +%H%M)
timestamp=$date$time
    
while [ $# -gt 0 ]
do
	case "$1" in
		"-I")
			buildType=I;
			tagMaps="-DtagMaps=true";
			compareMaps="-DcompareMaps=true";;
		"-N") 
			buildType=N;
			tagMaps="";
			compareMaps="";
			fetchTag="-DfetchTag=CVS=HEAD,GIT=origin/master";;

        "-fetchTag")
                fetchTag="$2"; shift;;

		"-baseBuilder")
			baseBuilderBranch="$2"; shift;;
			
		"-root")
			writableBuildRoot="$2"; shift;;
		
		"-support")
			supportDir="$2"; shift;;
				
		"-publish")
			publish="-DpublishToEclipse=true";;
			
		"-timestamp")
			timestamp="$2"; 
			date=${timestamp:0:8}
			time=${timestamp:8};
			shift;;
		
		"-noTag")
			noTag=true;;
			
		"-hudson")
			hudson=true;;
			
		"-email")
			resultsEmail="$2"; shift;;
			
		-*)
			echo >&2 usage: $0 [-I | -N]
			exit 1;;
		 *) break;;	 # terminate while loop
	esac
	shift
done


setProperties () {
	buildDirectory=$writableBuildRoot/$buildType$timestamp
	buildLabel=$buildType$date-$time
	javaHome=/opt/public/common/jdk1.7.0-latest

	pushd $supportDir
	launcherJar=$supportDir/$( find org.eclipse.releng.basebuilder/ -name "org.eclipse.equinox.launcher_*.jar" | sort | head -1 )
	popd
		
	#Properties for compilation boot classpaths
	JAVA70_HOME=/shared/common/jdk1.7.0-latest
	JAVA60_HOME=/shared/common/jdk-1.6.0_10
	JAVA50_HOME=/shared/common/jdk-1.5.0_16
	JAVA14_HOME=/shared/common/j2sdk1.4.2_19

	j2se142="$JAVA14_HOME/jre/lib/rt.jar:$JAVA14_HOME/jre/lib/jsse.jar:$JAVA14_HOME/jre/lib/jce.jar:$JAVA14_HOME/jre/lib/charsets.jar"
	j2se150="$JAVA50_HOME/jre/lib/rt.jar:$JAVA50_HOME/jre/lib/jsse.jar:$JAVA50_HOME/jre/lib/jce.jar:$JAVA50_HOME/jre/lib/charsets.jar"
	javase160="$JAVA60_HOME/jre/lib/resources.jar:$JAVA60_HOME/jre/lib/rt.jar:$JAVA60_HOME/jre/lib/jsse.jar:$JAVA60_HOME/jre/lib/jce.jar:$JAVA60_HOME/jre/lib/charsets.jar"
	javase170="$JAVA70_HOME/jre/lib/resources.jar:$JAVA70_HOME/jre/lib/rt.jar:$JAVA70_HOME/jre/lib/jsse.jar:$JAVA70_HOME/jre/lib/jce.jar:$JAVA70_HOME/jre/lib/charsets.jar"
}

updateRelengProject () {
	pushd $supportDir
	
	if [[ -d org.eclipse.orion.releng ]]; then
		rm -rf org.eclipse.orion.releng
	fi
	
	if [ "$buildType" == "N" -o -n "$noTag" ]; then 
		pushd $writableBuildRoot/gitClones/org.eclipse.orion.server
		git pull
		popd

		# Pull from client repo as well
		pushd $writableBuildRoot/gitClones/org.eclipse.orion.client
		git pull
		popd
	fi
	
	echo "[`date +%H\:%M\:%S`] Get org.eclipse.orion.releng"
	cp -r $writableBuildRoot/gitClones/org.eclipse.orion.server/releng/org.eclipse.orion.releng .
	echo "[`date +%H\:%M\:%S`] Done getting org.eclipse.orion.releng"

	echo "[`date +%H\:%M\:%S`] Get org.eclipse.orion.client.releng"
	cp -r $writableBuildRoot/gitClones/org.eclipse.orion.client/releng/org.eclipse.orion.client.releng/* org.eclipse.orion.releng
	echo "[`date +%H\:%M\:%S`] Done getting org.eclipse.client.releng"

	popd
}

updateBaseBuilder () {
    pushd $supportDir
    if [[ ! -d org.eclipse.releng.basebuilder_${basebuilderBranch} ]]; then
        echo "[start - `date +%H\:%M\:%S`] Get org.eclipse.releng.basebuilder_${basebuilderBranch}"
        cmd="cvs -d :pserver:anonymous@dev.eclipse.org:/cvsroot/eclipse $quietCVS ex -r $basebuilderBranch -d org.eclipse.releng.basebuilder_${basebuilderBranch} org.eclipse.releng.basebuilder"
        echo $cmd
        $cmd
        echo "[finish - `date +%H\:%M\:%S`] Done getting org.eclipse.releng.basebuilder_${basebuilderBranch}"
    fi

    echo "[`date +%H\:%M\:%S`] Getting org.eclipse.releng.basebuilder_${basebuilderBranch}"
    rm org.eclipse.releng.basebuilder
    ln -s ${supportDir}/org.eclipse.releng.basebuilder_${basebuilderBranch} org.eclipse.releng.basebuilder
    echo "[`date +%H\:%M\:%S`] Done setting org.eclipse.releng.basebuilder"
	popd
}

tagRepositories() {
	#do this for I builds and if -noTag was not specified
	if [ "$buildType" == "I" -a -z "$noTag" ]; then 
		pushd $writableBuildRoot/gitClones
		
        if [ ! -f git-map.sh ]; then
	        wget -O git-map.sh http://dev.eclipse.org/viewcvs/viewvc.cgi/e4/releng/org.eclipse.e4.builder/scripts/git-map.sh?view=co&content-type=text/plain
        fi
        if [ ! -f git-submission.sh ]; then
	        wget -O git-submission.sh http://dev.eclipse.org/viewcvs/viewvc.cgi/e4/releng/org.eclipse.e4.builder/scripts/git-submission.sh?view=co&content-type=text/plain
        fi
		
		#pull the server first to get the latest map files before updating with new tags
		cd $writableBuildRoot/gitClones/org.eclipse.orion.server
		git pull
	
		cd $writableBuildRoot/gitClones/org.eclipse.orion.client
		git pull

		cd $writableBuildRoot/gitClones
		/bin/bash $writableBuildRoot/gitClones/git-map.sh \
			$writableBuildRoot/gitClones \
			$writableBuildRoot/gitClones/org.eclipse.orion.server/releng/org.eclipse.orion.releng \
			git://git.eclipse.org/gitroot/orion/org.eclipse.orion.server.git \
			git://git.eclipse.org/gitroot/orion/org.eclipse.orion.client.git > maps.txt
			
		grep -v ^OK maps.txt | grep -v ^Executed >run.txt
		/bin/bash run.txt
		
		mkdir $writableBuildRoot/$buildType$timestamp
		cp report.txt $writableBuildRoot/$buildType$timestamp
		
		cd $writableBuildRoot/gitClones/org.eclipse.orion.server
		git add releng/org.eclipse.orion.releng/maps/orion.map
		git commit -m "Releng build tagging for $buildType$timestamp"
		git tag -f $buildType$timestamp   #tag the map file change
		
		git push
		git push --tags
	
		popd
	fi
}

runBuild () {
	cmd="$javaHome/bin/java -enableassertions -jar $launcherJar \
			-application org.eclipse.ant.core.antRunner \
			-buildfile $builderDir/buildWebIDE.xml \
			-Dbuilder=$builderDir/builder \
			-Dbase=$writableBuildRoot \
			-DbuildType=$buildType -Dtimestamp=$timestamp -DbuildLabel=$buildLabel \
			-DgitUser=$user \
			$tagMaps $compareMaps $fetchTag $publish \
			-DJ2SE-1.4=$j2se142 \
			-DJ2SE-1.5=$j2se150 \
			-DJavaSE-1.6=$javase160 \
			-DJavaSE-1.7=$javase170"
			
	echo "[`date +%H\:%M\:%S`] Launching Build"
	$cmd
	echo "[`date +%H\:%M\:%S`] Build Complete"
	
	#stop now if the build failed
	failure=$(sed -n '/BUILD FAILED/,/Total time/p' $writableBuildRoot/logs/current.log)
	if [[ ! -z $failure ]]; then
		compileMsg=""
		prereqMsg=""
		pushd $buildDirectory/plugins
		compileProblems=$( find . -name compilation.problem | cut -d/ -f2 )
		popd
		
		if [[ ! -z $compileProblems ]]; then
			compileMsg="Compile errors occurred in the following bundles:"
		fi
		if [[ -e $buildDirectory/prereqErrors.log ]]; then
			prereqMsg=`cat $buildDirectory/prereqErrors.log` 
		fi
		
		mailx -s "Orion Build : $buildLabel failed" $resultsEmail <<EOF
$compileMsg
$compileProblems

$prereqMsg

$failure
EOF
		exit
	fi
}

runTests () {
	cmd="$javaHome/bin/java -jar $launcherJar \
			-application org.eclipse.ant.core.antRunner \
			-buildfile $builderDir/builder/scripts/runTests.xml \
			-DbuildDirectory=$buildDirectory \
			-Dbuilder=$builderDir/builder \
			-Dbase=$writableBuildRoot \
			-DbuildType=$buildType \
			-Dtimestamp=$timestamp \
			-DbuildLabel=$buildLabel \
			$fetchTag \
			-DJ2SE-1.4=$j2se142 \
			-DJ2SE-1.5=$j2se150 \
			-DJavaSE-1.6=$javase160 \
			-DJavaSE-1.7=$javase170"
	
	echo "[`date +%H\:%M\:%S`] Starting Tests"
	$cmd
	echo "[`date +%H\:%M\:%S`] Ending Tests"
}

sendMail () {
	compileMsg=""
	prereqMsg=""
	failed=""
	
	tagMsg=`cat $buildDirectory/report.txt`
	testsMsg=$(sed -n '/<!--START-TESTS-->/,/<!--END-TESTS-->/p' $buildDirectory/$buildLabel/drop/index.html > mail.txt)
	testsMsg=$(cat mail.txt | sed s_href=\"_href=\"http://download.eclipse.org/orion/drops/$buildType$timestamp/_)
	rm mail.txt
	
	red=$(echo $testsMsg | grep "ff0000")
    if [[ ! -z $red ]]; then
		failed="tests failed"
    fi
	
	pushd $buildDirectory/plugins
	compileProblems=$( find . -name compilation.problem | cut -d/ -f2 )
	popd

	if [[ ! -z $compileProblems ]]; then
		failed="failed"
		compileMsg="Compile errors occurred in the following bundles:"
	fi
	
	if [[ -e $buildDirectory/prereqErrors.log ]]; then
		prereqMsg=`cat $buildDirectory/prereqErrors.log` 
	fi
	
	echo "[`date +%H\:%M\:%S`] Sending mail to $resultsEmail"
(
echo "From: e4Build@build.eclipse.org "
echo "To: $resultsEmail "
echo "MIME-Version: 1.0 "
echo "Content-Type: text/html; charset=us-ascii"
echo "Subject: Orion Build : $buildLabel $failed"
echo ""
echo "<html><head><title>Orion Build $buildLabel</title></head>" 
echo "<body>See here for the build results: <a href="http://download.eclipse.org/orion/drops/$buildType$timestamp">$buildLabel</a><br>" 
echo "<pre>$tagMsg</pre><br>$testsMsg<br>$compileMsg<br>$compileProblems<br>$prereqMsg</body></html>" 
) | /usr/lib/sendmail -t

}

publish () {
	echo "[`date +%H\:%M\:%S`] Publishing to eclipse.org"
	pushd $buildDirectory/$buildLabel

	scp -r drop $user@build.eclipse.org:/home/data/httpd/download.eclipse.org/orion/drops/$buildType$timestamp
	
	if [ $buildType = I ]; then
		scp -r $buildDirectory/plugins/org.eclipse.orion.doc.isv/jsdoc $user@build.eclipse.org:/home/data/httpd/download.eclipse.org/orion
	fi
	
	rsync --recursive --delete $writableBuildRoot/target/integration $user@build.eclipse.org:/home/data/httpd/download.eclipse.org/orion/updates
}

cd $writableBuildRoot

#tag before updateRelengProject so that we get the new map files
tagRepositories 
updateRelengProject
updateBaseBuilder
setProperties
runBuild

#for now, no tests or publishing when running hudson
if [ -z "$hudson" ]; then
	runTests
	publish
fi

sendMail

