#!/bin/bash
# A simple script for monitoring disk usage thresholds on an Orion server
# Note the FS and email values need to be plugged in manually

THRESHOLD=80
FS=<filesystem goes here>
OUTPUT=($(LC_ALL=C df -P ${FS}))
CURRENT=$(echo ${OUTPUT[11]} | sed 's/%//')
[ $CURRENT -gt $THRESHOLD ] && df -h | mail -s "Disk usage at ${CURRENT}% on OrionHub" -c <email-goes-here>
