# Copyright (c) 2013 IBM Corporation and others.
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
# A simple script to backup Orion metadata

workspaceHome=/opt/mnt/serverworkspace
backupHome=/home/admin/old

#back-up the current server using a folder based on date
dateString=`date +%Y%m%d-%H%M`
backupDir=${backupHome}/"metadata-"${dateString}
echo Backing up metadata server to $backupDir
mkdir $backupDir
cp -r $workspaceHome/.metadata/.plugins/org.eclipse.orion.server.core/ $backupDir
cp -r $workspaceHome/.metadata/.plugins/org.eclipse.orion.server.user.securestorage/ $backupDir

echo Backup complete
