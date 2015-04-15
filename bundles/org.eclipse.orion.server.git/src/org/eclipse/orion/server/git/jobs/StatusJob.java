/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.jobs;

import java.io.File;
import java.net.URI;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A job to perform a git status in the background.
 */
public class StatusJob extends GitJob {

	private static final int GIT_PERF_THRESHOLD = 10000;// 10 seconds
	private IPath filePath;
	private URI baseLocation;

	/**
	 * Creates job with given the file path within a git repository.
	 * 
	 * @param userRunningTask
	 * @param filePath
	 * @param baseLocation
	 */
	public StatusJob(String userRunningTask, Path filePath, URI baseLocation) {
		super(userRunningTask, false);
		this.filePath = filePath;
		this.baseLocation = baseLocation;
	}

	@Override
	protected IStatus performJob() {
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.git");
		Repository db = null;
		try {
			long t0 = System.currentTimeMillis();
			Set<Entry<IPath, File>> set = GitUtils.getGitDirs(this.filePath, Traverse.GO_UP).entrySet();
			File gitDir = set.iterator().next().getValue();
			if (gitDir == null) {
				logger.error("***** Git status failed to find Git directory for request: " + this.filePath);
				String msg = NLS.bind("Could not find repository for {0}", filePath);
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);
			}
			long t1 = System.currentTimeMillis();
			db = FileRepositoryBuilder.create(gitDir);
			Git git = new Git(db);
			org.eclipse.jgit.api.Status gitStatus = git.status().call();
			long t2 = System.currentTimeMillis();

			String relativePath = GitUtils.getRelativePath(this.filePath, set.iterator().next().getKey());
			IPath basePath = new Path(relativePath);
			org.eclipse.orion.server.git.objects.Status status = new org.eclipse.orion.server.git.objects.Status(this.baseLocation, db, gitStatus, basePath);
			ServerStatus result = new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, status.toJSON());
			if (logger.isDebugEnabled() && (t2 - t0) > GIT_PERF_THRESHOLD) {
				logger.debug("Slow git status. Finding git dir: " + (t1 - t0) + "ms. JGit status call: " + (t2 - t1) + "ms");
			}
			return result;
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when generating status for ref {0}", this.filePath);
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, msg, e);
		} finally {
			if (db != null) {
				// close the git repository
				db.close();
			}
		}
	}
}
