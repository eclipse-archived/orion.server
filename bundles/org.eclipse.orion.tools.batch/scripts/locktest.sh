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
#!/bin/bash
#
# This script runs a lock test repeatedly. Some file systems do not seem to reliquish locks immediately 
# after a JVM exit, so running in a loop can help expose file locking problems in the underlying file system
#!/bin/sh

for i in {1..1000}
do
   java -jar locktest.jar ~/lock.txt
done
