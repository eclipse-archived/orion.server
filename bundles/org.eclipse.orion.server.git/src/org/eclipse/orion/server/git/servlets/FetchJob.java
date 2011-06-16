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
package org.eclipse.orion.server.git.servlets;

import java.io.IOException;
import java.net.URISyntaxException;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.*;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * A job to perform a fetch operation in the background
 */
public class FetchJob extends GitJob {

	private final TaskInfo task;
	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;
	private IPath path;
	private String remote;

	public FetchJob(CredentialsProvider credentials, Path path) {
		super("Fetching", (GitCredentialsProvider) credentials); //$NON-NLS-1$

		// path: {remote}[/{branch}]/file/{...}
		this.path = path;
		this.remote = path.segment(0);
		this.task = createTask();
	}

	private TaskInfo createTask() {
		TaskInfo info = getTaskService().createTask();
		info.setMessage(NLS.bind("Fetching {0}...", remote));
		getTaskService().updateTask(info);
		return info;
	}

	private void doFetch() throws IOException, CoreException, JGitInternalException, InvalidRemoteException, URISyntaxException {
		Repository db = getRepository();
		String branch = getRemoteBranch();

		Git git = new Git(db);
		FetchCommand fc = git.fetch();

		RemoteConfig remoteConfig = new RemoteConfig(git.getRepository().getConfig(), remote);
		credentials.setUri(remoteConfig.getURIs().get(0));

		fc.setCredentialsProvider(credentials);
		fc.setRemote(remote);
		if (branch != null) {
			// refs/heads/{branch}:refs/remotes/{remote}/{branch}
			RefSpec spec = new RefSpec(Constants.R_HEADS + branch + ":" + Constants.R_REMOTES + remote + "/" + branch); //$NON-NLS-1$ //$NON-NLS-2$
			fc.setRefSpecs(spec);
		}
		fc.call();
	}

	private Repository getRepository() throws IOException, CoreException {
		IPath p = null;
		if (path.segment(1).equals("file")) //$NON-NLS-1$
			p = path.removeFirstSegments(1);
		else
			p = path.removeFirstSegments(2);
		return new FileRepository(GitUtils.getGitDir(p));
	}

	private String getRemoteBranch() {
		if (path.segment(1).equals("file")) //$NON-NLS-1$
			return null;
		else
			return path.segment(1);
	}

	public TaskInfo getTask() {
		return task;
	}

	private ITaskService getTaskService() {
		BundleContext context = GitActivator.getDefault().getBundleContext();
		taskServiceRef = context.getServiceReference(ITaskService.class);
		if (taskServiceRef == null)
			throw new IllegalStateException("Task service not available"); //$NON-NLS-1$
		taskService = context.getService(taskServiceRef);
		if (taskService == null)
			throw new IllegalStateException("Task service not available"); //$NON-NLS-1$
		return taskService;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		IStatus result = Status.OK_STATUS;
		try {
			doFetch();
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e);
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (JGitInternalException e) {
			result = getJGitInternalExceptionStatus(e, "An internal git error fetching git remote");
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e);
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e);
		}
		task.done(result);
		task.setMessage(NLS.bind("Fetching {0} done", remote));
		updateTask();
		taskService = null;
		GitActivator.getDefault().getBundleContext().ungetService(taskServiceRef);
		return Status.OK_STATUS;
	}

	private void updateTask() {
		getTaskService().updateTask(task);
	}
}
