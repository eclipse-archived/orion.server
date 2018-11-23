/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.jobs;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.Cookie;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;

/**
 * A job to perform a pull operation in the background
 */
public class PullJob extends GitJob {

	private IPath path;
	private String projectName;

	public PullJob(String userRunningTask, CredentialsProvider credentials, Path path, boolean force) {
		super(userRunningTask, true, (GitCredentialsProvider) credentials);
		// path: file/{...}
		this.path = path;
		this.projectName = path.lastSegment();
		// this.force = force; // TODO: enable when JGit starts to support this option
		setName(NLS.bind("Pulling {0}", projectName));
		setFinalMessage(NLS.bind("Pulling {0} done", projectName));
		setTaskExpirationTime(TimeUnit.DAYS.toMillis(7));
	}

	public PullJob(String userRunningTask, CredentialsProvider credentials, Path path, boolean force, Object cookie) {
		this(userRunningTask, credentials, path, force);
		this.cookie = (Cookie) cookie;
	}

	private IStatus doPull(IProgressMonitor monitor) throws IOException, GitAPIException, CoreException {
		ProgressMonitor gitMonitor = new EclipseGitProgressTransformer(monitor);
		Repository db = null;
		try {
			db = FileRepositoryBuilder.create(GitUtils.getGitDir(path));
			Git git = new Git(db);
			PullCommand pc = git.pull();
			pc.setProgressMonitor(gitMonitor);
			pc.setCredentialsProvider(credentials);
			pc.setTransportConfigCallback(new TransportConfigCallback() {
				@Override
				public void configure(Transport t) {
					credentials.setUri(t.getURI());
					if (t instanceof TransportHttp && cookie != null) {
						HashMap<String, String> map = new HashMap<String, String>();
						map.put(GitConstants.KEY_COOKIE, cookie.getName() + "=" + cookie.getValue());
						((TransportHttp) t).setAdditionalHeaders(map);
					}
				}
			});
			PullResult pullResult = pc.call();

			if (monitor.isCanceled()) {
				return new Status(IStatus.CANCEL, GitActivator.PI_GIT, "Cancelled");
			}

			// handle result
			if (pullResult.isSuccessful()) {
				return Status.OK_STATUS;
			}
			FetchResult fetchResult = pullResult.getFetchResult();

			IStatus fetchStatus = FetchJob.handleFetchResult(fetchResult);
			if (!fetchStatus.isOK()) {
				return fetchStatus;
			}

			MergeStatus mergeStatus = pullResult.getMergeResult().getMergeStatus();
			if (!mergeStatus.isSuccessful())
				return new Status(IStatus.ERROR, GitActivator.PI_GIT, mergeStatus.name());
		} finally {
			if (db != null) {
				db.close();
			}
		}
		return Status.OK_STATUS;
	}

	@Override
	protected IStatus performJob(IProgressMonitor monitor) {
		IStatus result = Status.OK_STATUS;
		try {
			result = doPull(monitor);
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Pulling error", e);
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (GitAPIException e) {
			result = getGitAPIExceptionStatus(e, "Pulling error");
		} catch (JGitInternalException e) {
			return getJGitInternalExceptionStatus(e, "Pulling error");
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Pulling error", e);
		}
		return result;
	}
}
