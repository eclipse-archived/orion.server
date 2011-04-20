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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
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
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * A job to perform a fetch operation in the background
 */
public class PushJob extends Job {

	private final CredentialsProvider credentials;
	private final TaskInfo task;
	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;
	private Path p;
	private String srcRef;

	public PushJob(CredentialsProvider credentials, Path path, String srcRef) {
		super("Pushing"); //$NON-NLS-1$
		this.credentials = credentials;
		this.p = path;
		this.srcRef = srcRef;
		this.task = createTask();
	}

	private TaskInfo createTask() {
		TaskInfo info = getTaskService().createTask();
		info.setMessage(NLS.bind("Pushing {0}...", p.segment(0)));
		getTaskService().updateTask(info);
		return info;
	}

	private void doPush() throws IOException, CoreException, JGitInternalException, InvalidRemoteException, URISyntaxException, JSONException {
		if (p.segment(2).equals("file")) {
			// /git/remote/{remote}/{branch}/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
			Repository db = new FileRepository(gitDir);
			Git git = new Git(db);

			// ObjectId ref = db.resolve(srcRef);
			RefSpec spec = new RefSpec(srcRef + ":" + Constants.R_HEADS + p.segment(1));
			Iterable<PushResult> resultIterable = git.push().setRemote(p.segment(0)).setRefSpecs(spec).call();
			PushResult pushResult = resultIterable.iterator().next();
			JSONObject result = new JSONObject();
			for (final RemoteRefUpdate rru : pushResult.getRemoteUpdates()) {
				final String rm = rru.getRemoteName();
				// final String sr = rru.isDelete() ? null : rru.getSrcRef();
				if (p.segment(1).equals(Repository.shortenRefName(rm))) {
					result.put(GitConstants.KEY_RESULT, rru.getStatus());
					break;
				}
				// TODO: return results for all updated branches once push is available for remote, see bug 342727, comment 1
			}
		}
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
			doPush();
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e); //$NON-NLS-1$
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (JGitInternalException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e); //$NON-NLS-1$
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e); //$NON-NLS-1$
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e); //$NON-NLS-1$
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
