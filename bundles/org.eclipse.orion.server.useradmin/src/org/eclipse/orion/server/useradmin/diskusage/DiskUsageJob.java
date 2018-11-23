/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin.diskusage;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A job used to calculate the disk usage for each user. The resulting value is saved in the user profile as well as the timestamp
 * for when it was calculated. The job is run every twelve hours (twice a day).
 *  
 * @author Anthony Hunter
 */
public class DiskUsageJob extends Job {
	private Logger logger;

	public DiskUsageJob() {
		super("Orion Disk Usage Data");
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		logger = LoggerFactory.getLogger("org.eclipse.orion.server.account"); //$NON-NLS-1$
		if (!updateDiskUsageData()) {
			if (logger.isInfoEnabled()) {
				logger.info("Orion disk usage user data job waiting for user metadata service");
			}
			schedule(5000);
			return Status.OK_STATUS;
		}
		// run the disk usage job again in twelve hours (twice a day).
		schedule(43200000);
		return Status.OK_STATUS;
	}

	private boolean updateDiskUsageData() {
		try {
			if (logger.isInfoEnabled()) {
				logger.info("Orion disk usage user data job started"); //$NON-NLS-1$
			}

			IMetaStore metaStore = OrionConfiguration.getMetaStore();
			List<String> userids = metaStore.readAllUsers();
			for (String userId : userids) {
				File userRoot = metaStore.getUserHome(userId).toLocalFile(EFS.NONE, null);
				String diskUsage = getFolderSize(userRoot);
				UserInfo userInfo = metaStore.readUser(userId);
				// try to store the disk usage timestamp and value in the user profile
				userInfo.setProperty(UserConstants2.DISK_USAGE, diskUsage);
				userInfo.setProperty(UserConstants2.DISK_USAGE_TIMESTAMP, new Long(System.currentTimeMillis()).toString());
				metaStore.updateUser(userInfo);
			}
			if (logger.isInfoEnabled()) {
				logger.info("Orion disk usage user data updated"); //$NON-NLS-1$
			}

		} catch (CoreException e) {
			logger.error("Cannot complete Orion disk usage user data job", e); //$NON-NLS-1$
			return false;
		}
		return true;
	}

	private String getFolderSize(File folder) {
		StringBuffer commandOutput = new StringBuffer();
		Process process;
		try {
			// execute the "du -hs" command to get the space used by this folder
			process = Runtime.getRuntime().exec("du -hs " + folder.toString());
			process.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line = "";
			while ((line = reader.readLine()) != null) {
				commandOutput.append(line + "\n");
			}

		} catch (Exception e) {
			return "unknown";
		}

		String size = commandOutput.toString();
		if (size.indexOf("\t") == -1) {
			return "unknown";
		}
		return size.substring(0, size.indexOf("\t"));
	}
}