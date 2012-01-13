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
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.storage.file.FileRepository;
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
		super("Fetching", userRunningTask, "Fetching...", false, false, (GitCredentialsProvider) credentials);
		// path: {remote}[/{branch}]/file/{...}
		this.path = path;
		this.remote = path.segment(0);
		this.force = force;
		this.branch = path.segment(1).equals("file") ? null : path.segment(1); //$NON-NLS-1$
		setName(branch == null ? NLS.bind("Fetching {0}", remote) : NLS.bind("Fetching {0}/{1}", new Object[] {remote, branch}));
		setMessage(branch == null ? NLS.bind("Fetching {0}...", remote) : NLS.bind("Fetching {0}/{1}...", new Object[] {remote, branch}));
		setFinalMessage(NLS.bind("Fetching {0} done", remote));
	}

	private IStatus doFetch() throws IOException, CoreException, JGitInternalException, InvalidRemoteException, URISyntaxException {
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
		return handleFetchResult(fetchResult);
	}

	static IStatus handleFetchResult(FetchResult fetchResult) {
		// handle result
		for (TrackingRefUpdate updateRes : fetchResult.getTrackingRefUpdates()) {
			Result res = updateRes.getResult();
			// handle status for given ref
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
				case REJECTED_CURRENT_BRANCH :
					// show warning, as only force fetch can finish successfully 
					return new Status(IStatus.WARNING, GitActivator.PI_GIT, res.name());
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
		return new FileRepository(GitUtils.getGitDir(p));
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
		} catch (JGitInternalException e) {
			result = getJGitInternalExceptionStatus(e, "An internal git error fetching git remote");
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e);
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e);
		}
		return result;
	}
}
