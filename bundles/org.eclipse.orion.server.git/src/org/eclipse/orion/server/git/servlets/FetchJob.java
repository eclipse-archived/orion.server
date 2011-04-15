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
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * A job to perform a fetch operation in the background
 */
public class FetchJob extends Job {

	private final TaskInfo task;
	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;
	private IPath gitDir;
	private String remote;

	public FetchJob(Path path, String user) {
		super("Fetching"); //$NON-NLS-1$

		this.gitDir = path.removeFirstSegments(2);
		this.remote = path.segment(0);
		this.task = createTask();
	}

	private TaskInfo createTask() {
		TaskInfo info = getTaskService().createTask();
		info.setMessage(NLS.bind("Fetching {0}...", remote));
		getTaskService().updateTask(info);
		return info;
	}

	private void doFetch() throws IOException, CoreException, JGitInternalException, InvalidRemoteException {
		Repository db = new FileRepository(GitUtils.getGitDir(gitDir));
		Git git = new Git(db);
		git.fetch().setRemote(remote).call();
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
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e); //$NON-NLS-1$
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (JGitInternalException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e); //$NON-NLS-1$
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e); //$NON-NLS-1$
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
