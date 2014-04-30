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
# A simple metadata serverworkspace creator for SimpleMetaStoreV1. Used to reverse engineer
# version one of the simple metadata storage to be used for migration tests. Tell the user to run:
# % cd serverworkspace
# % find . -type d > directories.txt
# % find . -type f > files.txt
# Once you get these files back, you can copy directories.txt, files.txt and simple_metadata_creator.sh
# into a folder and run:
# % ./simple_metadata_creator.sh
# 
# @author Anthony Hunter
#
IFS=$'\n'
DIRECTORIES=directories.txt
FILES=files.txt
if [ ! -e ${DIRECTORIES} ]; then
	echo "There is no ${DIRECTORIES} in the current folder"
	exit 0;
fi
if [ ! -e ${FILES} ]; then
	echo "There is no ${FILES} in the current folder"
	exit 0;
fi

DIRS=`cat ${DIRECTORIES} | sed "s/^./serverworkspace/"`

for FOLDER in ${DIRS} ; do
	if [ ! -e "${FOLDER}" ]; then
		echo mkdir -p ${FOLDER}
		mkdir -p ${FOLDER}
	fi
done

FILEZ=`cat ${FILES} | sed "s/^./serverworkspace/"`

for FILE in ${FILEZ} ; do
	if [ ! -e "${FILE}" ]; then
		echo touch ${FILE}
		touch ${FILE}
	fi
done
