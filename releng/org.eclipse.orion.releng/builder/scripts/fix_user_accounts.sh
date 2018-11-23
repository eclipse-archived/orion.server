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
# Fixes a legacy metadata store Users.prefs file.
#
LOGFILE=${0/sh/log}
echo Output is saved in ${LOGFILE} | tee ${LOGFILE}
USERS=`egrep "/Id=" Users.prefs | egrep -v "^-" | wc -l`
USER_WORKSPACES=`egrep "/Name=" Workspaces.prefs | egrep -v '^-' | wc -l`
USER_LIST=`egrep "/Name=" Workspaces.prefs | egrep -v '^-' | awk -F'/' '{print $1}' | sort -u`

for USER in ${USER_LIST}; do
	HAS_ENTRY=`egrep "^${USER}/Workspaces" Users.prefs | wc -l`
	if [ ${HAS_ENTRY} -eq 0 ]; then
		echo ${USER} had no account in Users.prefs, added a new account entry | tee -a ${LOGFILE}
		echo "${USER}/Id=${USER}" >> Users.prefs
		echo "${USER}/Name=Unnamed User" >> Users.prefs
		echo "${USER}/UserName=${USER}" >> Users.prefs
		echo "${USER}/UserRights=[{\"Method\"\:15,\"Uri\"\:\"/users/${USER}\"},{\"Method\"\:15,\"Uri\"\:\"/workspace/${USER}\"},{\"Method\"\:15,\"Uri\"\:\"/workspace/${USER}/*\"},{\"Method\"\:15,\"Uri\"\:\"/file/${USER}\"},{\"Method\"\:15,\"Uri\"\:\"/file/${USER}/*\"}]" >> Users.prefs
		echo "${USER}/UserRightsVersion=3" >> Users.prefs
		echo "${USER}/Workspaces=[{\"Id\"\:\"${USER}\",\"LastModified\"\:1396548624828}]" >> Users.prefs
	else
		HAS_WORKSPACE_ENTRY=`egrep ${USER}/Workspaces Users.prefs | egrep "\"${USER}\"" | wc -l`
	        if [ ${HAS_WORKSPACE_ENTRY} -eq 0 ]; then
			echo ${USER} had an account in Users.prefs but the workspace and user rights were incorrect, modified their account entry | tee -a ${LOGFILE}
			egrep -v "^${USER}/UserRights|^${USER}/Workspaces" Users.prefs > Users.prefs.saved
			mv Users.prefs.saved Users.prefs
			echo "${USER}/UserRights=[{\"Method\"\:15,\"Uri\"\:\"/users/${USER}\"},{\"Method\"\:15,\"Uri\"\:\"/workspace/${USER}\"},{\"Method\"\:15,\"Uri\"\:\"/workspace/${USER}/*\"},{\"Method\"\:15,\"Uri\"\:\"/file/${USER}\"},{\"Method\"\:15,\"Uri\"\:\"/file/${USER}/*\"}]" >> Users.prefs
			echo "${USER}/Workspaces=[{\"Id\"\:\"${USER}\",\"LastModified\"\:1396548624828}]" >> Users.prefs
		else
			echo ${USER} has a good account entry in Users.prefs | tee -a ${LOGFILE}
		fi
	fi
done

GOOD_ACCOUNT=`egrep good ${LOGFILE} | wc -l`
ADDED_ACCOUNT=`egrep "had no account" ${LOGFILE} | wc -l`
MODIFIED_ACCOUNT=`egrep "workspace and user rights were incorrect" ${LOGFILE} | wc -l`
echo
echo SUMMARY | tee -a ${LOGFILE}
echo There were only ${USERS} user accounts in Users.prefs | tee -a ${LOGFILE}
echo There are ${USER_WORKSPACES} user workspaces in Workspaces.prefs, one per user | tee -a ${LOGFILE}
echo There were ${GOOD_ACCOUNT} users with good accounts in Users.prefs | tee -a ${LOGFILE}
echo There were ${ADDED_ACCOUNT} users with no accounts in Users.prefs, so one was added | tee -a ${LOGFILE}
echo There were ${MODIFIED_ACCOUNT} users who had a workspace that was modified in Users.prefs, so they have recovered their old projects but may have lost work in a newly created project | tee -a ${LOGFILE}

