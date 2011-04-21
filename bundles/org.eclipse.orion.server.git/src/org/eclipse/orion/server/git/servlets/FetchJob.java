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

import com.jcraft.jsch.JSchException;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.jsch.HostFingerprintException;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * A job to perform a fetch operation in the background
 */
public class FetchJob extends Job {

	private final CredentialsProvider credentials;
	private final TaskInfo task;
	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;
	private IPath gitDir;
	private String remote;

	public FetchJob(CredentialsProvider credentials, Path path) {
		super("Fetching"); //$NON-NLS-1$

		this.credentials = credentials;
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

	private void doFetch() throws IOException, CoreException, JGitInternalException, InvalidRemoteException, URISyntaxException {
		Repository db = new FileRepository(GitUtils.getGitDir(gitDir));
		Git git = new Git(db);
		FetchCommand cc = git.fetch();

		RemoteConfig remoteConfig = new RemoteConfig(git.getRepository().getConfig(), remote);
		((GitCredentialsProvider) credentials).setUri(remoteConfig.getURIs().get(0));

		cc.setCredentialsProvider(credentials);
		cc.setRemote(remote).call();

		cc.call();
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

	private static JSchException getJSchException(Throwable e) {
		if (e instanceof JSchException) {
			return (JSchException) e;
		}
		if (e.getCause() != null) {
			return getJSchException(e.getCause());
		}
		return null;
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
			JSchException jschEx = getJSchException(e);
			if (jschEx != null && jschEx instanceof HostFingerprintException) {
				HostFingerprintException cause = (HostFingerprintException) jschEx;
				result = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, cause.getMessage(), cause.formJson(), cause);
			} else {
				result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e); //$NON-NLS-1$
			}
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e); //$NON-NLS-1$
		} catch (Exception e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error cloning git repository", e); //$NON-NLS-1$
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
