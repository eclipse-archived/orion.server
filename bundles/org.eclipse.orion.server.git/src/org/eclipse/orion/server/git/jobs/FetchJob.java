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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.Cookie;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.IGitHubTokenProvider;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;

/**
 * A job to perform a fetch operation in the background
 */
@SuppressWarnings("restriction")
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

	public FetchJob(String userRunningTask, CredentialsProvider credentials, Path path, boolean force, Object cookie) {
		this(userRunningTask, credentials, path, force);
		this.cookie = (Cookie) cookie;
	}

	private void builtMessages() {
		// path is either : {remote}/file/{workspaceId}/{projectName}
		// or {remote}/{branch}/file/{workspaceId}/{projectName}
		String cloneName = path.lastSegment();
		if (branch == null) {
			Object[] bindings = new String[] { remote, cloneName };
			setName(NLS.bind("Fetching {0} for {1}", bindings));
			setFinalMessage(NLS.bind("Fetching {0} for {1} done.", bindings));
		} else {
			Object[] bindings = new String[] { remote, branch, cloneName };
			setName(NLS.bind("Fetching {0}/{1} for {2}", bindings));
			setFinalMessage(NLS.bind("Fetching {0}/{1} for {2} done.", bindings));
		}
	}

	private IStatus doFetch(IProgressMonitor monitor) throws IOException, CoreException, URISyntaxException, GitAPIException {
		ProgressMonitor gitMonitor = new EclipseGitProgressTransformer(monitor);
		Repository db = null;
		try {
			db = getRepository();
			Git git = Git.wrap(db);
			FetchCommand fc = git.fetch();
			fc.setProgressMonitor(gitMonitor);

			RemoteConfig remoteConfig = new RemoteConfig(git.getRepository().getConfig(), remote);
			credentials.setUri(remoteConfig.getURIs().get(0));
			if (this.cookie != null) {
				fc.setTransportConfigCallback(new TransportConfigCallback() {
					@Override
					public void configure(Transport t) {
						if (t instanceof TransportHttp && cookie != null) {
							HashMap<String, String> map = new HashMap<String, String>();
							map.put(GitConstants.KEY_COOKIE, cookie.getName() + "=" + cookie.getValue());
							((TransportHttp) t).setAdditionalHeaders(map);
						}
					}
				});
			}
			fc.setCredentialsProvider(credentials);
			fc.setRemote(remote);
			if (branch != null) {
				// refs/heads/{branch}:refs/remotes/{remote}/{branch}
				String remoteBranch = branch;
				if (branch.startsWith("for/")) {
					remoteBranch = branch.substring(4);
				}

				RefSpec spec = new RefSpec(Constants.R_HEADS + remoteBranch + ":" + Constants.R_REMOTES + remote + "/" + branch); //$NON-NLS-1$ //$NON-NLS-2$
				spec = spec.setForceUpdate(force);
				fc.setRefSpecs(spec);
			}
			FetchResult fetchResult = fc.call();
			if (monitor.isCanceled()) {
				return new Status(IStatus.CANCEL, GitActivator.PI_GIT, "Cancelled");
			}
			GitJobUtils.packRefs(db, gitMonitor);
			if (monitor.isCanceled()) {
				return new Status(IStatus.CANCEL, GitActivator.PI_GIT, "Cancelled");
			}
			return handleFetchResult(fetchResult);
		} finally {
			if (db != null) {
				db.close();
			}
		}
	}

	static IStatus handleFetchResult(FetchResult fetchResult) {
		// handle result
		for (TrackingRefUpdate updateRes : fetchResult.getTrackingRefUpdates()) {
			Result res = updateRes.getResult();
			switch (res) {
			case NOT_ATTEMPTED:
			case NO_CHANGE:
			case NEW:
			case FORCED:
			case FAST_FORWARD:
			case RENAMED:
				// do nothing, as these statuses are OK
				break;
			case REJECTED:
				return new Status(IStatus.WARNING, GitActivator.PI_GIT, "Fetch rejected, not a fast-forward.");
			case REJECTED_CURRENT_BRANCH:
				return new Status(IStatus.WARNING, GitActivator.PI_GIT, "Rejected because trying to delete the current branch.");
			default:
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
	protected IStatus performJob(IProgressMonitor monitor) {
		IStatus result = Status.OK_STATUS;
		try {
			result = doFetch(monitor);
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e);
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e);
		} catch (GitAPIException e) {
			result = getGitAPIExceptionStatus(e, "Error fetching git remote");

			if (matchMessage(JGitText.get().notAuthorized, e.getCause().getMessage())) {
				// HTTP connection problems are distinguished by exception message
				Repository db = null;
				try {
					db = getRepository();
					Git git = Git.wrap(db);
					RemoteConfig remoteConfig = new RemoteConfig(git.getRepository().getConfig(), remote);
					String repositoryUrl = remoteConfig.getURIs().get(0).toString();

					Enumeration<IGitHubTokenProvider> providers = GitCredentialsProvider.GetGitHubTokenProviders();
					while (providers.hasMoreElements()) {
						String authUrl = providers.nextElement().getAuthUrl(repositoryUrl, cookie);
						if (authUrl != null) {
							ServerStatus status = ServerStatus.convert(result);
							JSONObject data = status.getJsonData();
							data.put("GitHubAuth", authUrl); //$NON-NLS-1$
							break;
						}
					}
				} catch (Exception ex) {
					/* fail silently, no GitHub auth url will be returned */
				} finally {
					if (db != null) {
						db.close();
					}
				}
			}
		} catch (JGitInternalException e) {
			result = getJGitInternalExceptionStatus(e, "Error fetching git remote");
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error fetching git remote", e);
		}
		return result;
	}
}
