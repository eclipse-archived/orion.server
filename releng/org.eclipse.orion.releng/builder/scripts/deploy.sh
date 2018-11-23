#******************************************************************************
# Copyright (c) 2010, 2012 IBM Corporation and others.
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
# This script deploys a new Orion build to an orion server
# Usage: deploy -archive <archive> <host>

serverHome=/home/admin/current

while [ $# -gt 0 ]
do
        case "$1" in
                "-archive")
                        archive="$2"; shift;;

                 *) break;;      # terminate while loop
        esac
        shift
done

#default to using orion.eclipse.org
host=${1-"orion.eclipse.org"}

currDate=`date`
echo "----------------- $currDate ----------------"

if [ "$archive" ]
then
        echo "Deploying $archive to $host"
else
        echo 'Usage: deploy.sh -archive <archive-name> [<host>]'
        exit
fi

echo "Copying build to $host"
scp $archive admin@$host:$serverHome
if [ $? -gt 0 ]
then
        echo "Copy failed"
        exit
fi
archiveFile=`basename $archive`
echo "Invoking upgrade script on $host with archive file $archiveFile"
ssh -l admin $host /home/admin/current/upgrade.sh -archive $archiveFile
echo "Deploy complete in $SECONDS seconds"
echo "------------------------------------------"
