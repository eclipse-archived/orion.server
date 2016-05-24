#!/bin/sh
# Copyright (c) 2012, 2015 IBM Corporation and others.
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
# 
set -e

if [ $# -eq 1 -o $# -eq 2 ]; then
	buildType=$1
else
	echo "Usage: $0 [i | s | r | m]"
	echo "Example: $0 i"
	exit 1
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
localTarget=${WORKSPACE}/releng/org.eclipse.orion.server.repository/target
localUpdateSite=${localTarget}/repository/
echo "Using local update-site: $localUpdateSite"

# Determine remote update site we want to promote to (integration and maintenance are published on interim site, stable builds on milestone site, release builds on releases site)
case $buildType in
	m|M) remoteSite=maintenance ;;
	i|I) remoteSite=interim ;;
	s|S) remoteSite=milestones ;;
	r|R) remoteSite=releases ;;
	*) exit 0 ;;
esac
echo "Publishing as $remoteSite ( $buildType ) build"
remoteUpdateSiteBase="orion/updates/$remoteSite"
remoteUpdateSite="/home/data/httpd/download.eclipse.org/$remoteUpdateSiteBase"
remoteStableDir="/home/data/httpd/download.eclipse.org/orion/stable"
echo "Publishing to remote update-site: $remoteUpdateSite"

#figure out the next stable build number
if [ "$buildType" = "s" -o "$buildType" = "S" ]; then
	remoteDropDir="/home/data/httpd/download.eclipse.org/orion/drops/$(echo $buildType | tr '[:lower:]' '[:upper:]')*"
        existingStableBuilds=`ls -d ${remoteDropDir} 2> /dev/null | wc -l`
	nextStableBuild=$((existingStableBuilds + 1))
	dropFilesLabel=$(echo $buildType | tr '[:lower:]' '[:upper:]')$nextStableBuild
	echo "Publishing the next stable build as $dropFilesLabel"
fi

if [ -z "$dropFilesLabel" -a "$buildType" != i ]; then
	echo "Please provide a drop files label to append to the version (e.g. M5, RC1) if this is not an I build."
	exit 0
fi

# Prepare a temp directory
tmpDir="$localTarget/$JOB_NAME-publish-tmp"
rm -fr $tmpDir
mkdir -p $tmpDir
cd $tmpDir
echo "Working in `pwd`"

# Download and prepare Eclipse SDK, which is needed to process the update site
echo "Downloading eclipse to $PWD"
cp /home/data/httpd/download.eclipse.org/eclipse/downloads/drops4/R-4.5.2-201602121500/eclipse-SDK-4.5.2-linux-gtk-x86_64.tar.gz .
tar -xzf eclipse-SDK-4.5.2-linux-gtk-x86_64.tar.gz
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
rm eclipse-SDK-4.5.2-linux-gtk-x86_64.tar.gz

# Generate drop files
qualifiedVersion=$(egrep Build ${WORKSPACE}/bundles/org.eclipse.orion.server.core/about.properties | awk -F'[: \\\\]' '{print $4}')
echo "qualifiedVersion from the about.properties is $qualifiedVersion"
qualifier=${qualifiedVersion#*-}
echo "qualifier is $qualifier"
qualifier=${qualifier#v}
echo "qualifier without the v is $qualifier"
version=${qualifiedVersion%%-*}
echo "version is $version"
dropDir="$(echo $buildType | tr '[:lower:]' '[:upper:]')$qualifier"
echo "dropDir is $dropDir"
localDropDir=drops/$dropDir
echo "Creating drop files in local directory $tmpDir/$localDropDir"
mkdir -p $localDropDir

# Prepare local update site (merging is performed later, if required)
stagedUpdateSite="updates/$remoteSite/$dropDir"
mkdir -p $stagedUpdateSite
cp -R $localUpdateSite/* $stagedUpdateSite
echo "Copied $localUpdateSite to local directory $stagedUpdateSite."

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

case "$JOB_NAME" in
*-dev)
    echo "hudson job is orion-server-dev"
    CLIENT_JOB=orion-client-dev
    ;;
*-stable)
    echo "hudson job is orion-server-stable"
    CLIENT_JOB=orion-client-stable
    ;;
*)
    echo "hudson job is neither orion-server-dev or orion-server-stable"
    exit 0;
    ;;
esac
echo "hudson client job is ${CLIENT_JOB}"
CLIENT_WORKSPACE=${HUDSON_HOME}/jobs/${CLIENT_JOB}/workspace
				
if [ -d ${CLIENT_WORKSPACE}/built-js ] ; then
	for file in built-editor.zip built-compare.zip built-codeEdit.zip ; do \
		cp ${CLIENT_WORKSPACE}/built-js/${file} ${localDropDir}/${file}
	echo "Copied ${file}"
	done
else
	echo "Did not copy built-editor.css built-editor.js built-editor.min.js etc."
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
	ServerTestFailure=`egrep 'Tests run: ' ${WORKSPACE}/tests/org.eclipse.orion.server.tests/target/surefire-reports/org.eclipse.orion.server.tests.AllServerTests.txt | egrep 'FAILURE' | wc -l`
	if [ "${ServerTestFailure}" -ne 0 ]; then
		echo "Tests failure, so do not deploy build."
	fi
else
	#tests did not run
	ServerTestResults="Tests run: 0 <<< FAILURE!" 
	ServerTestFailure="1"
	echo "Tests did not run, so do not deploy build."
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

#copy the build to the final drop location
remoteDropDir=/home/data/httpd/download.eclipse.org/orion/drops/$dropDir
echo "copy build ${dropDir} to ${remoteDropDir}"
mkdir -p $remoteDropDir
cp -R $localDropDir/* $remoteDropDir/

# Ensure p2.mirrorURLs property is used in update site
echo "Setting p2.mirrorsURL to http://www.eclipse.org/downloads/download.php?format=xml&file=/$remoteUpdateSiteBase"
./eclipse/eclipse -nosplash --launcher.suppressErrors -clean -debug -application org.eclipse.wtp.releng.tools.addRepoProperties -vmargs -DartifactRepoDirectory=$PWD/$stagedUpdateSite -Dp2MirrorsURL="http://www.eclipse.org/downloads/download.php?format=xml&file=/$remoteUpdateSiteBase"

# Create p2.index file
if [ ! -e "$stagedUpdateSite/p2.index" ]; then
	echo "Creating p2.index file"
	echo "version = 1" > $stagedUpdateSite/p2.index
	echo "metadata.repository.factory.order = content.xml,\!" >> $stagedUpdateSite/p2.index
	echo "artifact.repository.factory.order = artifacts.xml,\!" >> $stagedUpdateSite/p2.index
fi

# Backup then clean remote update site
echo "Creating backup of remote update site."
if [ -d "$remoteUpdateSite" ]; then
	if [ -d BACKUP ]; then
		rm -fr BACKUP
	fi
	mkdir BACKUP
	cp -R $remoteUpdateSite BACKUP
	rm -fr $remoteUpdateSite
fi

echo "Publishing local $stagedUpdateSite directory to remote update site $remoteUpdateSite/$dropDir"
mkdir -p $remoteUpdateSite
cp -R $stagedUpdateSite $remoteUpdateSite

# Create the composite update site
cat > p2.composite.repository.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project name="p2 composite repository">
<target name="default">
<p2.composite.repository>
<repository compressed="true" location="${remoteUpdateSite}" name="${JOB_NAME}"/>
<add>
<repository location="${dropDir}"/>
</add>
</p2.composite.repository>
</target>
</project>
EOF

echo "Update the composite update site"
./eclipse/eclipse -nosplash --launcher.suppressErrors -clean -debug -application org.eclipse.ant.core.antRunner -buildfile p2.composite.repository.xml default

# deploy the successful build
if [ "${SKIP_DEPLOY}" != "true" ]; then
	if [ "${ServerTestFailure}" -eq 0 ]; then
		if [[ "${JOB_NAME}" == *-dev ]]; then
			echo "Deploying successful build to orion.eclipse.org."
			echo "ssh ahunter@build.eclipse.org ./deploy.sh -archive ${remoteDropDir}/eclipse-orion-${version}-linux.gtk.x86_64.zip"
			ssh ahunter@build.eclipse.org ./deploy.sh -archive ${remoteDropDir}/eclipse-orion-${version}-linux.gtk.x86_64.zip
		else
			echo "Not deploying this successful build."
		fi
	else
		echo "Not deploying this build, tests failed."
	fi
else
		echo "Not deploying this build, deploy is disabled."
fi

# Clean up
echo "Cleaning up"
#rm -fr eclipse
#rm -fr update-site
