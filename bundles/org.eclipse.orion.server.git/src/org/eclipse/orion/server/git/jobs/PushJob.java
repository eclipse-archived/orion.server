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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.Cookie;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitConstants;
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

	public PushJob(String userRunningTask, CredentialsProvider credentials, Path path, String srcRef, boolean tags, boolean force, Object cookie) {
		this(userRunningTask, credentials, path, srcRef, tags, force);
		this.cookie = (Cookie) cookie;
	}

	private IStatus doPush(IProgressMonitor monitor) throws IOException, CoreException, URISyntaxException, GitAPIException {
		ProgressMonitor gitMonitor = new EclipseGitProgressTransformer(monitor);
		// /git/remote/{remote}/{branch}/file/{path}
		File gitDir = GitUtils.getGitDir(path.removeFirstSegments(2));
		// this set will contain only OK status or UP_TO_DATE status
		Set<RemoteRefUpdate.Status> statusSet = new HashSet<RemoteRefUpdate.Status>();
		Repository db = null;
		try {
			db = FileRepositoryBuilder.create(gitDir);
			Git git = new Git(db);

			PushCommand pushCommand = git.push();
			pushCommand.setProgressMonitor(gitMonitor);
			pushCommand.setTransportConfigCallback(new TransportConfigCallback() {
				@Override
				public void configure(Transport t) {
					credentials.setUri(t.getURI());
					if (t instanceof TransportHttp && cookie != null) {
						HashMap<String, String> map = new HashMap<String, String>();
						map.put(GitConstants.KEY_COOKIE, cookie.getName() + "=" + cookie.getValue());
						((TransportHttp) t).setAdditionalHeaders(map);
					}
				}
			});
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
			if (monitor.isCanceled()) {
				return new Status(IStatus.CANCEL, GitActivator.PI_GIT, "Cancelled");
			}
			PushResult pushResult = resultIterable.iterator().next();
			for (final RemoteRefUpdate rru : pushResult.getRemoteUpdates()) {
				if (monitor.isCanceled()) {
					return new Status(IStatus.CANCEL, GitActivator.PI_GIT, "Cancelled");
				}
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
		} finally {
			if (db != null) {
				db.close();
			}
		}
		if (statusSet.contains(RemoteRefUpdate.Status.OK))
			// if there is OK status in the set -> something was updated
			return Status.OK_STATUS;
		else
			// if there is no OK status in the set -> only UP_TO_DATE status is possible
			return new Status(IStatus.WARNING, GitActivator.PI_GIT, RemoteRefUpdate.Status.UP_TO_DATE.name());
	}

	@Override
	protected IStatus performJob(IProgressMonitor monitor) {
		IStatus result = Status.OK_STATUS;
		try {
			result = doPush(monitor);
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
