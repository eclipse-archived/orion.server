#!/bin/bash
#******************************************************************************
# Copyright (c) 2014 IBM Corporation and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#     IBM Corporation - initial API and implementation
#*******************************************************************************
#
# @author Anthony Hunter
#
# Creates a script that will cleanup all the errors after a metadata migration that look like:
# org.eclipse.orion.server.config - Meta File Error, root contains invalid metadata: folder /home/data/nfs/serverworkspace/GP/GP
#
# Run this script and it will create another script called invalid_metadata_cleanup_YYMMDD.sh
# Review the script and then run ./invalid_metadata_cleanup_YYMMDD.sh
#
IFS=$'\n'
LOGFILE=./.log
if [ ! -e ${LOGFILE} ]; then
	echo "There is no ${LOGFILE} in the current folder, cd to serverworkspace/.metadata first"
	exit 0;
fi
SCRIPT=invalid_metadata_cleanup_`date +%Y%m%d`.sh
echo Cleanup script is saved in ${SCRIPT}
touch ${SCRIPT}

FOLDERS=`egrep "Meta File Error, root contains invalid metadata: folder" ${LOGFILE} | awk -F':' '{print $NF}' | sed -e 's/^ folder //' | sort -u`

for FOLDER in ${FOLDERS} ; do
	if [ -e "${FOLDER}" ]; then
		echo "rm -rf \"${FOLDER}\"" >> ${SCRIPT}
	fi
done
