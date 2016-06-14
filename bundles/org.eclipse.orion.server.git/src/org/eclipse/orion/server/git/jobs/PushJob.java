/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

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
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.IGitHubTokenProvider;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A job to perform a push operation in the background
 */
@SuppressWarnings("restriction")
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
		Repository db = null;
		JSONObject result = new JSONObject();
		try {
			db = FileRepositoryBuilder.create(gitDir);
			Git git = Git.wrap(db);

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

			boolean pushToGerrit = branch.startsWith("for/");
			RefSpec spec = new RefSpec(srcRef + ':' + (pushToGerrit ? "refs/" : Constants.R_HEADS) + branch);
			pushCommand.setRemote(remote).setRefSpecs(spec);
			if (tags)
				pushCommand.setPushTags();
			pushCommand.setForce(force);
			Iterable<PushResult> resultIterable = pushCommand.call();
			if (monitor.isCanceled()) {
				return new Status(IStatus.CANCEL, GitActivator.PI_GIT, "Cancelled");
			}
			PushResult pushResult = resultIterable.iterator().next();
			boolean error = false;
			JSONArray updates = new JSONArray();
			result.put(GitConstants.KEY_COMMIT_MESSAGE, pushResult.getMessages());
			result.put(GitConstants.KEY_UPDATES, updates);
			for (final RemoteRefUpdate rru : pushResult.getRemoteUpdates()) {
				if (monitor.isCanceled()) {
					return new Status(IStatus.CANCEL, GitActivator.PI_GIT, "Cancelled");
				}
				final String rm = rru.getRemoteName();
				// check status only for branch given in the URL or tags
				if (branch.equals(Repository.shortenRefName(rm)) || rm.startsWith(Constants.R_TAGS) || rm.startsWith(Constants.R_REFS + "for/")) {
					JSONObject object = new JSONObject();
					RemoteRefUpdate.Status status = rru.getStatus();
					if (status != RemoteRefUpdate.Status.UP_TO_DATE || !rm.startsWith(Constants.R_TAGS)) {
						object.put(GitConstants.KEY_COMMIT_MESSAGE, rru.getMessage());
						object.put(GitConstants.KEY_RESULT, status.name());
						TrackingRefUpdate refUpdate = rru.getTrackingRefUpdate();
						if (refUpdate != null) {
							object.put(GitConstants.KEY_REMOTENAME, Repository.shortenRefName(refUpdate.getLocalName()));
							object.put(GitConstants.KEY_LOCALNAME, Repository.shortenRefName(refUpdate.getRemoteName()));
						} else {
							object.put(GitConstants.KEY_REMOTENAME, Repository.shortenRefName(rru.getSrcRef()));
							object.put(GitConstants.KEY_LOCALNAME, Repository.shortenRefName(rru.getRemoteName()));
						}
						updates.put(object);
					}
					if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE)
						error = true;
				}
				// TODO: return results for all updated branches once push is available for remote, see bug 352202
			}
			// needs to handle multiple
			result.put("Severity", error ? "Error" : "Ok");
		} catch (JSONException e) {
		} finally {
			if (db != null) {
				db.close();
			}
		}
		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
	}

	@Override
	protected IStatus performJob(IProgressMonitor monitor) {
		IStatus result = Status.OK_STATUS;
		try {
			result = doPush(monitor);
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (InvalidRemoteException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e);
			LogHelper.log(result);
		} catch (GitAPIException e) {
			result = getGitAPIExceptionStatus(e, "Error pushing git remote"); //$NON-NLS-1$

			if (matchMessage(JGitText.get().notAuthorized, e.getCause().getMessage())) {
				// HTTP connection problems are distinguished by exception message
				Repository db = null;
				try {
					File gitDir = GitUtils.getGitDir(path.removeFirstSegments(2));
					db = FileRepositoryBuilder.create(gitDir);
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
			LogHelper.log(result);
		} catch (JGitInternalException e) {
			result = getJGitInternalExceptionStatus(e, "Error pushing git remote");
			LogHelper.log(result);
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git remote", e);
			LogHelper.log(result);
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error pushing git repository", e);
			LogHelper.log(result);
		}
		return result;
	}
}
