#!/bin/sh
# Copyright (c) 2012, 2014 IBM Corporation and others.
# All rights reserved.   This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#   IBM - Initial API and implementation

# Script may take 5-6 command line parameters:
# Hudson job name: ${JOB_NAME}
# Hudson build id: ${BUILD_ID}
# Hudson workspace: ${WORKSPACE}
# $1: Build type: n(ightly), m(aintenance), s(table), r(elease)
# $2: Whether to merge the update site with an existing one: (y)es, (n)o
# $3: Whether to generate udpate-site and SDK drop files: (y)es, (n)o
# $4: An optional label to append to the version string when creating drop files, e.g. M5 or RC1
# 
if [ $# -eq 3 -o $# -eq 4 ]; then
	buildType=$1
	merge=$2
	dropFiles=$3
	if [ -n "$4" ]; then
		dropFilesLabel=$4
	fi
else
	if [ $# -ne 0 ]; then
		exit 1
	fi
fi

if [ -z "$JOB_NAME" ]; then
	echo "Error there is no Hudson JOB_NAME defined"; 
	exit 0
fi

if [ -z "$BUILD_ID" ]; then
	echo "Error there is no Hudson BUILD_ID defined"; 
	exit 0
fi

if [ -z "$WORKSPACE" ]; then
	echo "Error there is no Hudson WORKSPACE defined"; 
	exit 0
fi

# Determine the local update site we want to publish from
localUpdateSite=${WORKSPACE}/releng/org.eclipse.orion.server.repository/target/repository/
echo "Using local update-site: $localUpdateSite"

# Select the build type
if [ -z "$buildType" ]; then
	echo -n "Please select which type of build you want to publish to [i(ntegration), m(aintenance), s(table), r(elease)]: "
	read buildType
fi
echo "Publishing as $buildType build"

# Determine remote update site we want to promote to (integration and maintenance are published on interim site, stable builds on milestone site, release builds on releases site)
case $buildType in
	m|M) remoteSite=maintenance ;;
	i|I) remoteSite=interim ;;
	s|S) remoteSite=milestones ;;
	r|R) remoteSite=releases ;;
	*) exit 0 ;;
esac
remoteUpdateSiteBase="orion/updates/$remoteSite"
remoteUpdateSite="/home/data/httpd/download.eclipse.org/$remoteUpdateSiteBase"
echo "Publishing to remote update-site: $remoteUpdateSite"

if [ -d "$remoteUpdateSite" ]; then
	if [ -z "$merge" ]; then
		echo -n "Do you want to merge with the existing update-site? [(y)es, (n)o]:"
		read merge
	fi
	if [ "$merge" != y -a "$merge" != n ]; then
		exit 0
	fi
else
	echo "Remote update site does not exist $remoteUpdateSite, cannot merge with the existing update-site"
	merge=n
fi
echo "Merging with existing site: $merge"

if [ -z "$dropFiles" ]; then
	echo -n "Do you want to create update-site and SDK drop files? [(y)es, (n)o]:"
	read dropFiles
fi
if [ "$dropFiles" != y -a "$dropFiles" != n ]; then
	exit 0
fi
echo "Generating update-site and SDK drop files: $dropFiles"

if [ -z "$dropFilesLabel" -a "$dropFiles" = y -a "$buildType" != i ]; then
	echo -n "Please enter a drop files label to append to the version (e.g. M5, RC1) or leave empty to skip this [<empty>]:"
	read dropFilesLabel
fi

# Prepare a temp directory
tmpDir="$JOB_NAME-publish-tmp"
rm -fr $tmpDir
mkdir -p $tmpDir/update-site
cd $tmpDir
echo "Working in `pwd`"

# Download and prepare Eclipse SDK, which is needed to merge update site and postprocess repository 
echo "Downloading eclipse to $PWD"
cp /home/data/httpd/download.eclipse.org/eclipse/downloads/drops4/R-4.3.2-201402211700/eclipse-SDK-4.3.2-linux-gtk-x86_64.tar.gz .
tar -xzf eclipse-SDK-4.3.2-linux-gtk-x86_64.tar.gz
cd eclipse
chmod 700 eclipse
cd ..
if [ ! -d "eclipse" ]; then
	echo "Failed to download an Eclipse SDK, being needed for provisioning."
	exit
fi
# Prepare Eclipse SDK to provide WTP releng tools (used to postprocess repository, i.e set p2.mirrorsURL property)
echo "Installing WTP Releng tools"
./eclipse/eclipse -nosplash --launcher.suppressErrors -clean -debug -application org.eclipse.equinox.p2.director -repository http://download.eclipse.org/webtools/releng/repository/ -installIUs org.eclipse.wtp.releng.tools.feature.feature.group
# Clean up
echo "Cleaning up"
rm eclipse-SDK-4.3.2-linux-gtk-x86_64.tar.gz

# Prepare local update site (merging is performed later, if required)
cp -R $localUpdateSite/* update-site/
echo "Copied $localUpdateSite to local directory update-site."

# Generate drop files
if [ "$dropFiles" = y ]; then
	qualifiedVersion=$(find $localUpdateSite/features/ -maxdepth 1 | grep "org.eclipse.orion_" | sed 's/.jar$//')
	echo "qualifiedVersion is $qualifiedVersion"
	qualifiedVersion=${qualifiedVersion#*_}
	echo "qualifiedVersion is $qualifiedVersion"
        qualifier=${qualifiedVersion##*.}
        echo "qualifier is $qualifier"
	qualifier=${qualifier#v}
	echo "qualifier is $qualifier"
        version=${qualifiedVersion%.*}
        echo "version is $version"
        dropDir="$(echo $buildType | tr '[:lower:]' '[:upper:]')$qualifier"
        echo "dropDir is $dropDir"
        localDropDir=drops/$dropDir
        echo "Creating drop files in local directory `pwd`/$localDropDir"
	mkdir -p $localDropDir

	# Append drop file suffix if one is specified				
	if [ -n "$dropFilesLabel" ]; then
		version=$version$dropFilesLabel
		echo "version is now $version"
	elif [ "$buildType" != r -a "$buildType" != R ]; then
		version="$(echo $buildType | tr '[:lower:]' '[:upper:]')$qualifier"
		echo "version is now $version"
	else
		echo "version is now $version"
	fi
				
	for zip in win32.win32.x86 macosx.cocoa.x86 linux.gtk.x86 macosx.cocoa.x86_64 win32.win32.x86_64 linux.gtk.x86_64 ; do
		cp ${WORKSPACE}/releng/org.eclipse.orion.server.repository/target/products/org.eclipse.orion-${zip}.zip ${localDropDir}/eclipse-orion-${version}-${zip}.zip
		#md5sum $localDropDir/org.eclipse.orion-${version}-${zip}.zip > $localDropDir/org.eclipse.orion-${version}-${zip}.zip.md5
		echo "Created org.eclipse.orion-${version}-${zip}.zip"
	done

	CLIENT_WORKSPACE=${HUDSON_HOME}/jobs/orion-client/workspace				
	if [ -d ${CLIENT_WORKSPACE}/built-js ] ; then
		for file in built-editor.zip built-editor-amd.min.js built-editor-amd.js built-editor.min.js built-editor.js built-editor.css built-compare.zip built-compare-amd.min.js built-compare-amd.js built-compare.min.js built-compare.js built-compare.css OrionIconFont-Regular.eot OrionIconFont-Regular.woff OrionIconFont-Regular.ttf OrionIconFont-Regular.svg ; do \
			cp ${CLIENT_WORKSPACE}/built-js/${file} ${localDropDir}/${file}
		echo "Copied ${file}"
		done
	else
		echo "Did not copy built-editor.css built-editor.js built-editor.min.js etc."
	fi

	if [ -e ${WORKSPACE}/releng/org.eclipse.orion.server.repository/gerritfs/gerritfs.jar ] ; then
		cp ${WORKSPACE}/releng/org.eclipse.orion.server.repository/gerritfs/gerritfs.jar ${localDropDir}/gerritfs.jar
	else
		echo "Did not copy gerritfs.jar"
	fi
	
	#generating build.cfg file to be referenced from downloads web page
	echo "hudson.job.name=${JOB_NAME}" > $localDropDir/build.cfg
	echo "hudson.job.id=${BUILD_NUMBER} (${jobDir##*/})" >> $localDropDir/build.cfg
	echo "hudson.job.url=${BUILD_URL}" >> $localDropDir/build.cfg

	if [ -e ${WORKSPACE}/tests/org.eclipse.orion.server.tests/target/surefire-reports/org.eclipse.orion.server.tests.AllServerTests.txt ]; then
		#copy the test results
		ServerTestResults=`egrep "Tests run" ${WORKSPACE}/tests/org.eclipse.orion.server.tests/target/surefire-reports/org.eclipse.orion.server.tests.AllServerTests.txt`
		cp ${WORKSPACE}/tests/org.eclipse.orion.server.tests/target/surefire-reports/org.eclipse.orion.server.tests.AllServerTests.txt ${localDropDir}
		cp ${WORKSPACE}/tests/org.eclipse.orion.server.tests/target/surefire-reports/TEST-org.eclipse.orion.server.tests.AllServerTests.xml ${localDropDir}
	else
		#tests did not run
		ServerTestResults="Tests run: 0 <<< FAILURE!" 
	fi
	
	#generate the index.html for the build
	cat ${WORKSPACE}/releng/org.eclipse.orion.server.repository/html/build.index.html | sed -e "s/@version@/${version}/" -e "s/@repbuildlabel@/${version}/" -e "s/@ServerTestResults@/${ServerTestResults}/" > ${localDropDir}/index.html

	#copy the download script
	cp ${WORKSPACE}/releng/org.eclipse.orion.server.repository/html/build.download.php ${localDropDir}/download.php

	#copy the consoleText
	JOB_DIR=${HUDSON_HOME}/jobs/${JOB_NAME}/builds/${BUILD_NUMBER}
	if [ -d ${JOB_DIR} ] ; then
		cp ${JOB_DIR}/log ${localDropDir}/consoleText.txt
	else
		echo "Did not copy consoleText"
	fi

	remoteDropDir=/home/data/httpd/download.eclipse.org/orion/drops/$dropDir
	mkdir -p $remoteDropDir
	cp -R $localDropDir/* $remoteDropDir/
fi

if [ "$merge" = y ]; then
	echo "Merging existing site into local one."
	./eclipse/eclipse -nosplash --launcher.suppressErrors -clean -debug -application org.eclipse.equinox.p2.metadata.repository.mirrorApplication -source file:$remoteUpdateSite -destination file:update-site
	./eclipse/eclipse -nosplash --launcher.suppressErrors -clean -debug -application org.eclipse.equinox.p2.artifact.repository.mirrorApplication -source file:$remoteUpdateSite -destination file:update-site
	echo "Merged $remoteUpdateSite into local directory update-site."
fi

# Ensure p2.mirrorURLs property is used in update site
echo "Setting p2.mirrorsURL to http://www.eclipse.org/downloads/download.php?format=xml&file=/$remoteUpdateSiteBase"
./eclipse/eclipse -nosplash --launcher.suppressErrors -clean -debug -application org.eclipse.wtp.releng.tools.addRepoProperties -vmargs -DartifactRepoDirectory=$PWD/update-site -Dp2MirrorsURL="http://www.eclipse.org/downloads/download.php?format=xml&file=/$remoteUpdateSiteBase"

# Create p2.index file
if [ ! -e "update-site/p2.index" ]; then
	echo "Creating p2.index file"
	echo "version = 1" > update-site/p2.index
	echo "metadata.repository.factory.order = content.xml,\!" >> update-site/p2.index
	echo "artifact.repository.factory.order = artifacts.xml,\!" >> update-site/p2.index
fi
		
# Backup then clean remote update site
echo "Creating backup of remote update site."
if [ -d "$remoteUpdateSite" ]; then
	if [ -d BACKUP ]; then
		rm -fr BACKUP
	fi
	mkdir BACKUP
	cp -R $remoteUpdateSite/* BACKUP/
	rm -fr $remoteUpdateSite
fi

echo "Publishing contents of local update-site directory to remote update site $remoteUpdateSite"
mkdir -p $remoteUpdateSite
cp -R update-site/* $remoteUpdateSite/

# Clean up
echo "Cleaning up"
#rm -fr eclipse
#rm -fr update-site
