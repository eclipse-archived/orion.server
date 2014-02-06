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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;

/**
 * A job to perform a push operation in the background
 */
public class PushJob extends GitJob {

	private Path path;
	private String remote;
	private String branch;
	private String srcRef;
	private boolean tags;
	private boolean force;

	public PushJob(String userRunningTask, CredentialsProvider credentials, Path path, String srcRef, boolean tags, boolean force) {
		super(userRunningTask, true, (GitCredentialsProvider) credentials);
		this.path = path;
		this.remote = path.segment(0);
		this.branch = GitUtils.decode(path.segment(1));
		this.srcRef = srcRef;
		this.tags = tags;
		this.force = force;
		setFinalMessage(NLS.bind("Pushing {0} done", path.segment(0)));
		setTaskExpirationTime(TimeUnit.DAYS.toMillis(7));
	}

	private IStatus doPush() throws IOException, CoreException, URISyntaxException, GitAPIException {
		// /git/remote/{remote}/{branch}/file/{path}
		File gitDir = GitUtils.getGitDir(path.removeFirstSegments(2));
		Repository db = FileRepositoryBuilder.create(gitDir);
		Git git = new Git(db);

		PushCommand pushCommand = git.push();

		RemoteConfig remoteConfig = new RemoteConfig(git.getRepository().getConfig(), remote);
		credentials.setUri(remoteConfig.getURIs().get(0));
		pushCommand.setCredentialsProvider(credentials);

		boolean pushToGerrit = branch.startsWith("refs/for") && GitUtils.isGerrit(git.getRepository().getConfig());
		RefSpec spec = new RefSpec(srcRef + ':' + (pushToGerrit ? "" : Constants.R_HEADS) + branch);
		pushCommand.setRemote(remote).setRefSpecs(spec);
		if (tags)
			pushCommand.setPushTags();
		pushCommand.setForce(force);
		Iterable<PushResult> resultIterable = pushCommand.call();
		PushResult pushResult = resultIterable.iterator().next();
		// this set will contain only OK status or UP_TO_DATE status
		Set<RemoteRefUpdate.Status> statusSet = new HashSet<RemoteRefUpdate.Status>();
		for (final RemoteRefUpdate rru : pushResult.getRemoteUpdates()) {
			final String rm = rru.getRemoteName();
			// check status only for branch given in the URL or tags
			if (branch.equals(Repository.shortenRefName(rm)) || rm.startsWith(Constants.R_TAGS)) {
				RemoteRefUpdate.Status status = rru.getStatus();
				// any status different from UP_TO_DATE and OK should generate warning
				if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE)
					return new Status(IStatus.WARNING, GitActivator.PI_GIT, status.name(), new Throwable(rru.getMessage()));
				// add OK or UP_TO_DATE status to the set
				statusSet.add(status);
			}
			// TODO: return results for all updated branches once push is available for remote, see bug 352202
		}
		// close the git repository
		db.close();
		if (statusSet.contains(RemoteRefUpdate.Status.OK))
			// if there is OK status in the set -> something was updated
			return Status.OK_STATUS;
		else
			// if there is no OK status in the set -> only UP_TO_DATE status is possible
			return new Status(IStatus.WARNING, GitActivator.PI_GIT, RemoteRefUpdate.Status.UP_TO_DATE.name());
	}

	@Override
	protected IStatus performJob() {
		IStatus result = Status.OK_STATUS;
		try {
			result = doPush();
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e);
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e);
		} catch (GitAPIException e) {
			result = getGitAPIExceptionStatus(e, "Error pushing git remote");
		} catch (JGitInternalException e) {
			result = getJGitInternalExceptionStatus(e, "Error pushing git remote");
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git repository", e);
		}
		return result;
	}
}
