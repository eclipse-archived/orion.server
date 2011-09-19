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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.*;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;

/**
 * A job to perform a push operation in the background
 */
public class PushJob extends GitJob {

	private final TaskInfo task;
	private Path path;
	private String srcRef;
	private boolean tags;
	private boolean force;

	public PushJob(CredentialsProvider credentials, Path path, String srcRef, boolean tags, boolean force) {
		super("Pushing", (GitCredentialsProvider) credentials);
		this.path = path;
		this.srcRef = srcRef;
		this.tags = tags;
		this.task = createTask();
		this.force = force;
	}

	private TaskInfo createTask() {
		TaskInfo info = getTaskService().createTask();
		info.setMessage(NLS.bind("Pushing {0}...", path.segment(0)));
		getTaskService().updateTask(info);
		return info;
	}

	private IStatus doPush() throws IOException, CoreException, JGitInternalException, InvalidRemoteException, URISyntaxException, JSONException {
		// /git/remote/{remote}/{branch}/file/{path}
		File gitDir = GitUtils.getGitDir(path.removeFirstSegments(2));
		Repository db = new FileRepository(gitDir);
		Git git = new Git(db);

		PushCommand pushCommand = git.push();

		RemoteConfig remoteConfig = new RemoteConfig(git.getRepository().getConfig(), path.segment(0));
		credentials.setUri(remoteConfig.getURIs().get(0));
		pushCommand.setCredentialsProvider(credentials);

		// ObjectId ref = db.resolve(srcRef);
		RefSpec spec = new RefSpec(srcRef + ":" + Constants.R_HEADS + path.segment(1)); //$NON-NLS-1$
		pushCommand.setRemote(path.segment(0)).setRefSpecs(spec);
		if (tags)
			pushCommand.setPushTags();
		pushCommand.setForce(force);
		Iterable<PushResult> resultIterable = pushCommand.call();
		PushResult pushResult = resultIterable.iterator().next();
		// this set will contain only OK status or UP_TO_DATE status
		Set<RemoteRefUpdate.Status> statusSet = new HashSet<RemoteRefUpdate.Status>();
		for (final RemoteRefUpdate rru : pushResult.getRemoteUpdates()) {
			final String rm = rru.getRemoteName();
			// final String sr = rru.isDelete() ? null : rru.getSrcRef();
			// check status only for branch given in the URL or tags
			if (path.segment(1).equals(Repository.shortenRefName(rm)) || rm.startsWith(Constants.R_TAGS)) {
				RemoteRefUpdate.Status status = rru.getStatus();
				// any status different from UP_TO_DATE and OK should generate warning
				if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE)
					// return new Status(IStatus.WARNING, GitActivator.PI_GIT, status.name(), new Throwable(rru.getMessage()));
					// workaround for bug 355931
					return new Status(IStatus.WARNING, GitActivator.PI_GIT, status.name() + ": " + rru.getMessage()); //$NON-NLS-1$
				// add OK or UP_TO_DATE status to the set
				statusSet.add(status);
			}
			// TODO: return results for all updated branches once push is available for remote, see bug 342727, comment 1
		}
		if (statusSet.contains(RemoteRefUpdate.Status.OK))
			// if there is OK status in the set -> something was updated
			return Status.OK_STATUS;
		else
			// if there is no OK status in the set -> only UP_TO_DATE status is possible
			return new Status(IStatus.WARNING, GitActivator.PI_GIT, RemoteRefUpdate.Status.UP_TO_DATE.name());
	}

	public TaskInfo getTask() {
		return task;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		IStatus result = Status.OK_STATUS;
		try {
			result = doPush();
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e);
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (JGitInternalException e) {
			result = getJGitInternalExceptionStatus(e, "Error pushing git remote");
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e);
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git repository", e);
		}
		task.done(result);
		task.setMessage(NLS.bind("Pushing {0} done", path.segment(0)));
		updateTask(task);
		cleanUp();
		return Status.OK_STATUS; // see bug 353190
	}
}
