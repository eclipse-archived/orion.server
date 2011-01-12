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

#default values, overridden by command line
writableBuildRoot=/web/builds
basebuilderBranch=v20101019

while [ $# -gt 0 ]
do
	case "$1" in
		"-I")
			buildType="-DbuildType=I";
			tagMaps="-DtagMaps=true";
			compareMaps="-DcompareMaps=true";;
		"-N") 
			buildType="-DbuildType=N";
			tagMaps="";
			compareMaps="";
			fetchTag="-DfetchTag=HEAD";;

		"-baseBuilder")
			baseBuilderBranch="$2"; shift;;
			
		"-root")
			writableBuildRoot="$2"; shift;;
			
		-*)
			echo >&2 usage: $0 [-I | -N]
			exit 1;;
		 *) break;;	 # terminate while loop
	esac
	shift
done

supportDir=$writableBuildRoot/base
builderDir=$supportDir/org.eclipse.e4.webide.releng

setJavaProperties () {
	javaHome=$writableBuildRoot/java/sun-150
	
	#Properties for compilation boot classpaths
	cdc10="$writableBuildRoot/java/foundation10/jclFoundation10/classes.zip"
	cdc11="$writableBuildRoot/java/foundation11/jclFoundation11/classes.zip"
	j2se142="$writableBuildRoot/java/sun-142/jre/lib/rt.jar:$writableBuildRoot/java/sun-142/jre/lib/jsse.jar:$writableBuildRoot/java/sun-142/jre/lib/jce.jar"
	j2se150="$writableBuildRoot/java/sun-150/jre/lib/rt.jar"
	javase160="$writableBuildRoot/java/sun-160/jre/lib/rt.jar"
}

updateRelengProject () {
	pushd $supportDir
	if [[ ! -d org.eclipse.e4.webide.releng ]]; then
	    cmd="cvs -d :ext:@ottcvs1.ottawa.ibm.com:/home/cvs/desktop co -d org.eclipse.e4.webide.releng org.eclipse.e4.webide.releng"
	else
	    cmd="cvs -d :ext:@ottcvs1.ottawa.ibm.com:/home/cvs/desktop update -d org.eclipse.e4.webide.releng "
	fi

	echo "[start - `date +%H\:%M\:%S`] Get org.eclipse.e4.webide.releng"	
	echo $cmd
	$cmd
	echo "[finish - `date +%H\:%M\:%S`] Done getting org.eclipse.e4.webide.releng"
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

    echo "[`date +%H\:%M\:%S`] Setting org.eclipse.releng.basebuilder_${basebuilderBranch}"
    rm org.eclipse.releng.basebuilder
    ln -s ${supportDir}/org.eclipse.releng.basebuilder_${basebuilderBranch} org.eclipse.releng.basebuilder
	popd
}

runBuild () {
	pushd $supportDir
	 
	launcherJar=$( find org.eclipse.releng.basebuilder/ -name "org.eclipse.equinox.launcher_*.jar" | sort | head -1 )
	
	cmd="$javaHome/bin/java -enableassertions -jar $launcherJar \
			-application org.eclipse.ant.core.antRunner \
			-buildfile $builderDir/buildWebIDE.xml \
			-Dbuilder=$builderDir/builder \
			-Dbase=$writableBuildRoot \
			$buildType $tagMaps $compareMaps $fetchTag \
			-DCDC-1.0/Foundation-1.0=$cdc10 \
			-DCDC-1.1/Foundation-1.1=$cdc11 \
			-DJ2SE-1.4=$j2se142 \
			-DJ2SE-1.5=$j2se150 \
			-DJavaSE-1.6=$javase160 \
			-DrunTests=true \
			-DlaunchWebIDE=true "
			
	echo "[start - `date +%H\:%M\:%S`] Launching Build"
	echo $cmd
	$cmd
	popd
}

cd $writableBuildRoot
setJavaProperties
updateRelengProject
updateBaseBuilder
runBuild

