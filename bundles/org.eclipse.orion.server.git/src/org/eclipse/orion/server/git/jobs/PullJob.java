/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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
import java.util.concurrent.TimeUnit;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.*;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.git.GitActivator;
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
		this.projectName = computeProjectName(path);
		// this.force = force; // TODO: enable when JGit starts to support this option
		setName(NLS.bind("Pulling {0}", projectName));
		setFinalMessage(NLS.bind("Pulling {0} done", projectName));
		setTaskExpirationTime(TimeUnit.DAYS.toMillis(7));
	}

	private String computeProjectName(Path requestPath) {
		if (requestPath.segmentCount() == 2) {
			//path format is /file/projectId
			try {
				ProjectInfo info = OrionConfiguration.getMetaStore().readProject(requestPath.segment(1));
				if (info != null)
					return info.getFullName();
			} catch (CoreException e) {
				//fall through and use path segment below
			}
		}
		return requestPath.lastSegment();
	}

	private IStatus doPull() throws IOException, GitAPIException, CoreException {
		Repository db = new FileRepository(GitUtils.getGitDir(path));

		Git git = new Git(db);
		PullCommand pc = git.pull();
		pc.setCredentialsProvider(credentials);
		pc.setTransportConfigCallback(new TransportConfigCallback() {
			@Override
			public void configure(Transport t) {
				credentials.setUri(t.getURI());
			}
		});
		PullResult pullResult = pc.call();

		// handle result
		if (pullResult.isSuccessful()) {
			return Status.OK_STATUS;
		} else {

			FetchResult fetchResult = pullResult.getFetchResult();

			IStatus fetchStatus = FetchJob.handleFetchResult(fetchResult);
			if (!fetchStatus.isOK()) {
				return fetchStatus;
			}

			MergeStatus mergeStatus = pullResult.getMergeResult().getMergeStatus();
			if (mergeStatus.isSuccessful()) {
				return Status.OK_STATUS;
			} else {
				return new Status(IStatus.ERROR, GitActivator.PI_GIT, mergeStatus.name());
			}
		}
	}

	@Override
	protected IStatus performJob() {
		IStatus result = Status.OK_STATUS;
		try {
			result = doPull();
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
