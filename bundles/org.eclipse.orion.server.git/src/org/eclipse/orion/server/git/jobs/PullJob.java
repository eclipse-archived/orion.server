/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
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
import java.net.URISyntaxException;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.*;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;

/**
 * A job to perform a pull operation in the background
 */
public class PullJob extends GitJob {

	private IPath path;
	private String name;

	public PullJob(String userRunningTask, CredentialsProvider credentials, Path path, boolean force) {
		super("Pulling", userRunningTask, (GitCredentialsProvider) credentials); //$NON-NLS-1$
		// path: file/{...}
		this.path = path;
		this.name = path.segmentCount() == 2 ? WebProject.fromId(path.segment(1)).getName() : path.lastSegment();
		// this.force = force; // TODO: enable when JGit starts to support this option
		this.task = createTask();
	}

	protected TaskInfo createTask() {
		TaskInfo info = getTaskService().createTask(NLS.bind("Pulling {0}", name), this.userId);
		info.setMessage(NLS.bind("Pulling {0}...", name));
		getTaskService().updateTask(info);
		return info;
	}

	private IStatus doPull() throws IOException, CoreException, JGitInternalException, URISyntaxException, GitAPIException {
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
	protected IStatus run(IProgressMonitor monitor) {
		IStatus result = Status.OK_STATUS;
		try {
			result = doPull();
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Pulling error", e);
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (JGitInternalException e) {
			result = getJGitInternalExceptionStatus(e, "An internal pulling error");
		} catch (GitAPIException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Pulling error", e);
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Pulling error", e);
		}
		task.done(result);
		task.setMessage(NLS.bind("Pulling {0} done", name));
		updateTask(task);
		cleanUp();
		return Status.OK_STATUS; // see bug 353190
	}
}
