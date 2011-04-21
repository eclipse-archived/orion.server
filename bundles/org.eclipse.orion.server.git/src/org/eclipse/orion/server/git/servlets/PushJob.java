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
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.*;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.jsch.HostFingerprintException;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * A job to perform a push operation in the background
 */
public class PushJob extends Job {

	private final CredentialsProvider credentials;
	private final TaskInfo task;
	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;
	private Path p;
	private String srcRef;
	private boolean tags;

	public PushJob(CredentialsProvider credentials, Path path, String srcRef, boolean tags) {
		super("Pushing"); //$NON-NLS-1$

		this.credentials = credentials;
		this.p = path;
		this.srcRef = srcRef;
		this.tags = tags;
		this.task = createTask();
	}

	private TaskInfo createTask() {
		TaskInfo info = getTaskService().createTask();
		info.setMessage(NLS.bind("Pushing {0}...", p.segment(0)));
		getTaskService().updateTask(info);
		return info;
	}

	private IStatus doPush() throws IOException, CoreException, JGitInternalException, InvalidRemoteException, URISyntaxException, JSONException {
		// /git/remote/{remote}/{branch}/file/{path}
		File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
		Repository db = new FileRepository(gitDir);
		Git git = new Git(db);

		PushCommand pushCommand = git.push();

		RemoteConfig remoteConfig = new RemoteConfig(git.getRepository().getConfig(), p.segment(0));
		((GitCredentialsProvider) credentials).setUri(remoteConfig.getURIs().get(0));
		pushCommand.setCredentialsProvider(credentials);

		// ObjectId ref = db.resolve(srcRef);
		RefSpec spec = new RefSpec(srcRef + ":" + Constants.R_HEADS + p.segment(1)); //$NON-NLS-1$
		pushCommand.setRemote(p.segment(0)).setRefSpecs(spec);
		if (tags)
			pushCommand.setPushTags();
		Iterable<PushResult> resultIterable = pushCommand.call();
		PushResult pushResult = resultIterable.iterator().next();
		for (final RemoteRefUpdate rru : pushResult.getRemoteUpdates()) {
			final String rm = rru.getRemoteName();
			// final String sr = rru.isDelete() ? null : rru.getSrcRef();
			if (p.segment(1).equals(Repository.shortenRefName(rm))) {
				if (rru.getStatus() != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK)
					return new Status(IStatus.WARNING, GitActivator.PI_GIT, rru.getStatus().name());
				break;
			}
			// TODO: return results for all updated branches once push is available for remote, see bug 342727, comment 1
		}
		return Status.OK_STATUS;
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
			result = doPush();
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e); //$NON-NLS-1$
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (JGitInternalException e) {
			JSchException jschEx = getJSchException(e);
			if (jschEx != null && jschEx instanceof HostFingerprintException) {
				HostFingerprintException cause = (HostFingerprintException) jschEx;
				result = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, cause.getMessage(), cause.formJson(), cause);
			} else {
				result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e); //$NON-NLS-1$
			}
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e); //$NON-NLS-1$
		} catch (Exception e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error cloning git repository", e); //$NON-NLS-1$
		}
		task.done(result);
		task.setMessage(NLS.bind("Pushing {0} done", p.segment(0)));
		updateTask();
		taskService = null;
		GitActivator.getDefault().getBundleContext().ungetService(taskServiceRef);
		return Status.OK_STATUS;
	}

	private void updateTask() {
		getTaskService().updateTask(task);
	}
}
