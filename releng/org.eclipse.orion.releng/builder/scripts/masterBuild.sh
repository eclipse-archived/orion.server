#!/bin/bash

#*******************************************************************************
# Copyright (c) 2010, 2011 IBM Corporation and others.
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
supportDir=/shared/eclipse/e4/build/e4
builderDir=$supportDir/org.eclipse.orion.releng
basebuilderBranch=v20101019
publish=""
user=aniefer
resultsEmail=orion-dev@eclipse.org

buildType=I
timestamp=$( date +%Y%m%d%H%M )
    
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

		"-baseBuilder")
			baseBuilderBranch="$2"; shift;;
			
		"-root")
			writableBuildRoot="$2"; shift;;
		
		"-support")
			supportDir="$2"; shift;;
				
		"-publish")
			publish="-DpublishToEclipse=true";;
			
		"-timestamp")
			timestamp="$2"; shift;;
			
		-*)
			echo >&2 usage: $0 [-I | -N]
			exit 1;;
		 *) break;;	 # terminate while loop
	esac
	shift
done


setProperties () {
	buildDirectory=$writableBuildRoot/$buildType$timestamp
	
	javaHome=/shared/common/jdk-1.6.x86_64
	
	pushd $supportDir
	launcherJar=$supportDir/$( find org.eclipse.releng.basebuilder/ -name "org.eclipse.equinox.launcher_*.jar" | sort | head -1 )
	popd
		
	#Properties for compilation boot classpaths
	JAVA60_HOME=/shared/common/jdk-1.6.0_10
	JAVA50_HOME=/shared/common/jdk-1.5.0_16
	JAVA14_HOME=/shared/common/j2sdk1.4.2_19

	j2se142="$JAVA14_HOME/jre/lib/rt.jar:$JAVA14_HOME/jre/lib/jsse.jar:$JAVA14_HOME/jre/lib/jce.jar:$JAVA14_HOME/jre/lib/charsets.jar"
	j2se150="$JAVA50_HOME/jre/lib/rt.jar:$JAVA50_HOME/jre/lib/jsse.jar:$JAVA50_HOME/jre/lib/jce.jar:$JAVA50_HOME/jre/lib/charsets.jar"
	javase160="$JAVA60_HOME/jre/lib/resources.jar:$JAVA60_HOME/jre/lib/rt.jar:$JAVA60_HOME/jre/lib/jsse.jar:$JAVA60_HOME/jre/lib/jce.jar:$JAVA60_HOME/jre/lib/charsets.jar"
}

updateRelengProject () {
	pushd $supportDir
	
	if [[ -d org.eclipse.orion.releng ]]; then
		rm -rf org.eclipse.orion.releng
	fi
	
	echo "[`date +%H\:%M\:%S`] Get org.eclipse.orion.releng"	

	git archive --format=tar --remote=/gitroot/e4/org.eclipse.orion.server.git master releng/org.eclipse.orion.releng | tar -xf -
	mv releng/org.eclipse.orion.releng org.eclipse.orion.releng;
	rm -rf releng

	echo "[`date +%H\:%M\:%S`] Done getting org.eclipse.orion.releng"
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

runBuild () {
	cmd="$javaHome/bin/java -enableassertions -jar $launcherJar \
			-application org.eclipse.ant.core.antRunner \
			-buildfile $builderDir/buildWebIDE.xml \
			-Dbuilder=$builderDir/builder \
			-Dbase=$writableBuildRoot \
			-DbuildType=$buildType -Dtimestamp=$timestamp \
			-DgitUser=$user \
			$tagMaps $compareMaps $fetchTag $publish \
			-DJ2SE-1.4=$j2se142 \
			-DJ2SE-1.5=$j2se150 \
			-DJavaSE-1.6=$javase160"
			
	echo "[`date +%H\:%M\:%S`] Launching Build"
	$cmd
	echo "[`date +%H\:%M\:%S`] Build Complete"
}

runTests () {
	cmd="$javaHome/bin/java -jar $launcherJar \
			-application org.eclipse.ant.core.antRunner \
			-buildfile $builderDir/builder/scripts/runTests.xml \
			-DbuildDirectory=$buildDirectory \
			-Dbuilder=$builderDir/builder \
			-Dbase=$writableBuildRoot \
			-DbuildLabel=$buildType$timestamp"
	
	echo "[`date +%H\:%M\:%S`] Starting Tests"
	$cmd
	echo "[`date +%H\:%M\:%S`] Ending Tests"
}

sendMail () {
	compileMsg=""
	prereqMsg=""
	failed=""
	
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
	
mailx -s "[orion-build] Orion Build : $buildType$timestamp $failed" $resultsEmail <<EOF

Check here for the build results: 
http://download.eclipse.org/e4/orion/drops/$buildType$timestamp

$compileMsg
$compileProblems
$prereqMsg

EOF

}

publish () {
	echo "[`date +%H\:%M\:%S`] Publishing to eclipse.org"
	pushd $buildDirectory/$buildType$timestamp
	mv drop $buildType$timestamp
	scp -r $buildType$timestamp $user@dev.eclipse.org:/home/data/httpd/download.eclipse.org/e4/orion/drops
	wget -O index.html http://download.eclipse.org/e4/orion/createIndex.php
	scp index.html $user@dev.eclipse.org:/home/data/httpd/download.eclipse.org/e4/orion
	
	if [ $buildType = I ]; then
		scp -r $buildDirectory/plugins/org.eclipse.orion.doc.isv/jsdoc $user@dev.eclipse.org:/home/data/httpd/download.eclipse.org/e4/orion
	fi
	
	sendMail
}

cd $writableBuildRoot

updateRelengProject
updateBaseBuilder
setProperties
runBuild
runTests
publish

