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
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;

/**
 * A job to perform a fetch operation in the background
 */
public class FetchJob extends GitJob {

	private IPath path;
	private String remote;
	private boolean force;
	private String branch; // can be null if fetching the whole branch

	public FetchJob(String userRunningTask, CredentialsProvider credentials, Path path, boolean force) {
		super(userRunningTask, true, (GitCredentialsProvider) credentials);
		// path: {remote}[/{branch}]/file/{...}
		this.path = path;
		this.remote = path.segment(0);
		this.force = force;
		this.branch = path.segment(1).equals("file") ? null : GitUtils.decode(path.segment(1)); //$NON-NLS-1$
		builtMessages();
		setTaskExpirationTime(TimeUnit.DAYS.toMillis(7));
	}

	private void builtMessages() {
		// path is either : {remote}/file/{workspaceId}/{projectName}
		// or {remote}/{branch}/file/{workspaceId}/{projectName}
		String cloneName = path.lastSegment();
		if (branch == null) {
			Object[] bindings = new String[] {remote, cloneName};
			setName(NLS.bind("Fetching {0} for {1}", bindings));
			setFinalMessage(NLS.bind("Fetching {0} for {1} done.", bindings));
		} else {
			Object[] bindings = new String[] {remote, branch, cloneName};
			setName(NLS.bind("Fetching {0}/{1} for {2}", bindings));
			setFinalMessage(NLS.bind("Fetching {0}/{1} for {2} done.", bindings));
		}
	}

	private IStatus doFetch() throws IOException, CoreException, URISyntaxException, GitAPIException {
		Repository db = getRepository();

		Git git = new Git(db);
		FetchCommand fc = git.fetch();

		RemoteConfig remoteConfig = new RemoteConfig(git.getRepository().getConfig(), remote);
		credentials.setUri(remoteConfig.getURIs().get(0));

		fc.setCredentialsProvider(credentials);
		fc.setRemote(remote);
		if (branch != null) {
			// refs/heads/{branch}:refs/remotes/{remote}/{branch}
			RefSpec spec = new RefSpec(Constants.R_HEADS + branch + ":" + Constants.R_REMOTES + remote + "/" + branch); //$NON-NLS-1$ //$NON-NLS-2$
			spec = spec.setForceUpdate(force);
			fc.setRefSpecs(spec);
		}
		FetchResult fetchResult = fc.call();
		git.getRepository().close();
		return handleFetchResult(fetchResult);
	}

	static IStatus handleFetchResult(FetchResult fetchResult) {
		// handle result
		for (TrackingRefUpdate updateRes : fetchResult.getTrackingRefUpdates()) {
			Result res = updateRes.getResult();
			switch (res) {
				case NOT_ATTEMPTED :
				case NO_CHANGE :
				case NEW :
				case FORCED :
				case FAST_FORWARD :
				case RENAMED :
					// do nothing, as these statuses are OK
					break;
				case REJECTED :
					return new Status(IStatus.WARNING, GitActivator.PI_GIT, "Fetch rejected, not a fast-forward.");
				case REJECTED_CURRENT_BRANCH :
					return new Status(IStatus.WARNING, GitActivator.PI_GIT, "Rejected because trying to delete the current branch.");
				default :
					return new Status(IStatus.ERROR, GitActivator.PI_GIT, res.name());
			}
		}
		return Status.OK_STATUS;
	}

	private Repository getRepository() throws IOException, CoreException {
		IPath p = null;
		if (path.segment(1).equals("file")) //$NON-NLS-1$
			p = path.removeFirstSegments(1);
		else
			p = path.removeFirstSegments(2);
		return FileRepositoryBuilder.create(GitUtils.getGitDir(p));
	}

	@Override
	protected IStatus performJob() {
		IStatus result = Status.OK_STATUS;
		try {
			result = doFetch();
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e);
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e);
		} catch (GitAPIException e) {
			result = getGitAPIExceptionStatus(e, "Error fetching git remote");
		} catch (JGitInternalException e) {
			result = getJGitInternalExceptionStatus(e, "Error fetching git remote");
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e);
		}
		return result;
	}
}
