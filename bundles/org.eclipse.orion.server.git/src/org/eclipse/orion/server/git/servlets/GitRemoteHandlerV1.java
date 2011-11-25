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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskServlet;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.*;
import org.eclipse.orion.server.git.jobs.*;
import org.eclipse.orion.server.git.objects.Remote;
import org.eclipse.orion.server.git.objects.RemoteBranch;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.*;

public class GitRemoteHandlerV1 extends ServletResourceHandler<String> {
	private ServletResourceHandler<IStatus> statusHandler;

	GitRemoteHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		try {
			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, path);
				case POST :
					return handlePost(request, response, path);
				case DELETE :
					return handleDelete(request, response, path);
			}

		} catch (Exception e) {
			String msg = NLS.bind("Failed to handle /git/remote request for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		return false;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		Path p = new Path(path);
		// FIXME: what if a remote or branch is named "file"?
		if (p.segment(0).equals("file")) { //$NON-NLS-1$
			// /git/remote/file/{path}
			File gitDir = GitUtils.getGitDir(p);
			Repository db = new FileRepository(gitDir);
			Set<String> configNames = db.getConfig().getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION);
			JSONObject result = new JSONObject();
			JSONArray children = new JSONArray();
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.REMOTE_LIST);
			for (String configName : configNames) {
				Remote remote = new Remote(cloneLocation, db, configName);
				children.put(remote.toJSON(false, null));
			}
			result.put(ProtocolConstants.KEY_CHILDREN, children);
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} else if (p.segment(1).equals("file")) { //$NON-NLS-1$
			// /git/remote/{remote}/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(1));
			Repository db = new FileRepository(gitDir);
			Set<String> configNames = db.getConfig().getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION);
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.REMOTE);
			for (String configName : configNames) {
				if (configName.equals(p.segment(0))) {
					Remote remote = new Remote(cloneLocation, db, configName);
					OrionServlet.writeJSONResponse(request, response, remote.toJSON());
					return true;
				}
			}
			String msg = NLS.bind("Couldn't find remote : {0}", p.segment(0));
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
		} else if (p.segment(2).equals("file")) { //$NON-NLS-1$
			// /git/remote/{remote}/{branch}/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
			Repository db = new FileRepository(gitDir);
			URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.REMOTE_BRANCH);
			Remote remote = new Remote(cloneLocation, db, p.segment(0));
			RemoteBranch remoteBranch = new RemoteBranch(cloneLocation, db, remote, p.segment(1));
			JSONObject result = remoteBranch.toJSON();
			if (result != null) {
				OrionServlet.writeJSONResponse(request, response, result);
				return true;
			}
			JSONObject errorData = new JSONObject();
			errorData.put(GitConstants.KEY_CLONE, cloneLocation);

			return statusHandler.handleRequest(request, response, new ServerStatus(new Status(IStatus.ERROR, GitActivator.PI_GIT, "No remote branch found: " + p.uptoSegment(2).removeTrailingSeparator()), HttpServletResponse.SC_NOT_FOUND, errorData));
		}
		return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request, \"/git/remote/{remote}/{branch}/file/{path}\" expected", null));
	}

	// remove remote
	private boolean handleDelete(HttpServletRequest request, HttpServletResponse response, String path) throws CoreException, IOException, URISyntaxException, JSONException, ServletException {
		Path p = new Path(path);
		if (p.segment(1).equals("file")) { //$NON-NLS-1$
			// expected path: /gitapi/remote/{remote}/file/{path}
			String remoteName = p.segment(0);

			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(1));
			Repository db = new FileRepository(gitDir);
			StoredConfig config = db.getConfig();
			config.unsetSection(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName);
			config.save();
			//TODO: handle result
			return true;
		}
		return false;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		Path p = new Path(path);
		if (p.segment(0).equals("file")) { //$NON-NLS-1$
			// handle adding new remote
			// expected path: /git/remote/file/{path}
			return addRemote(request, response, path);
		} else {
			JSONObject requestObject = OrionServlet.readJSONRequest(request);
			boolean fetch = Boolean.parseBoolean(requestObject.optString(GitConstants.KEY_FETCH, null));
			boolean pull = Boolean.parseBoolean(requestObject.optString(GitConstants.KEY_PULL, null));
			String srcRef = requestObject.optString(GitConstants.KEY_PUSH_SRC_REF, null);
			boolean tags = requestObject.optBoolean(GitConstants.KEY_PUSH_TAGS, false);
			boolean force = requestObject.optBoolean(GitConstants.KEY_FORCE, false);

			// prepare creds
			String username = requestObject.optString(GitConstants.KEY_USERNAME, null);
			char[] password = requestObject.optString(GitConstants.KEY_PASSWORD, "").toCharArray(); //$NON-NLS-1$
			String knownHosts = requestObject.optString(GitConstants.KEY_KNOWN_HOSTS, null);
			byte[] privateKey = requestObject.optString(GitConstants.KEY_PRIVATE_KEY, "").getBytes(); //$NON-NLS-1$
			byte[] publicKey = requestObject.optString(GitConstants.KEY_PUBLIC_KEY, "").getBytes(); //$NON-NLS-1$
			byte[] passphrase = requestObject.optString(GitConstants.KEY_PASSPHRASE, "").getBytes(); //$NON-NLS-1$

			GitCredentialsProvider cp = new GitCredentialsProvider(null, username, password, knownHosts);
			cp.setPrivateKey(privateKey);
			cp.setPublicKey(publicKey);
			cp.setPassphrase(passphrase);

			// if all went well, continue with fetch or push
			if (fetch) {
				return fetch(request, response, cp, path, force);
			} else if (pull) {
				return pull(request, response, cp, path, force);
			} else if (srcRef != null) {
				return push(request, response, path, cp, srcRef, tags, force);
			} else {
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Only Fetch:true is currently supported.", null));
			}
		}
	}

	// add new remote
	private boolean addRemote(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException, CoreException, URISyntaxException {
		// expected path: /git/remote/file/{path}
		Path p = new Path(path);
		JSONObject toPut = OrionServlet.readJSONRequest(request);
		String remoteName = toPut.optString(GitConstants.KEY_REMOTE_NAME, null);
		// remoteName is required
		if (remoteName == null || remoteName.isEmpty()) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Remote name must be provided", null));
		}
		String remoteURI = toPut.optString(GitConstants.KEY_REMOTE_URI, null);
		// remoteURI is required
		if (remoteURI == null || remoteURI.isEmpty()) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Remote URI must be provided", null));
		}
		String fetchRefSpec = toPut.optString(GitConstants.KEY_REMOTE_FETCH_REF, null);
		String remotePushURI = toPut.optString(GitConstants.KEY_REMOTE_PUSH_URI, null);
		String pushRefSpec = toPut.optString(GitConstants.KEY_REMOTE_PUSH_REF, null);

		File gitDir = GitUtils.getGitDir(p);
		Repository db = new FileRepository(gitDir);
		StoredConfig config = db.getConfig();

		RemoteConfig rc = new RemoteConfig(config, remoteName);
		rc.addURI(new URIish(remoteURI));
		// FetchRefSpec is required, but default version can be generated
		// if it isn't provided
		if (fetchRefSpec == null || fetchRefSpec.isEmpty()) {
			fetchRefSpec = String.format("+refs/heads/*:refs/remotes/%s/*", remoteName); //$NON-NLS-1$
		}
		rc.addFetchRefSpec(new RefSpec(fetchRefSpec));
		// pushURI is optional
		if (remotePushURI != null && !remotePushURI.isEmpty())
			rc.addPushURI(new URIish(remotePushURI));
		// PushRefSpec is optional
		if (pushRefSpec != null && !pushRefSpec.isEmpty())
			rc.addPushRefSpec(new RefSpec(pushRefSpec));

		rc.update(config);
		config.save();

		URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.REMOTE_LIST);
		Remote remote = new Remote(cloneLocation, db, remoteName);
		JSONObject result = new JSONObject();
		result.put(ProtocolConstants.KEY_LOCATION, remote.getLocation());
		OrionServlet.writeJSONResponse(request, response, result);
		response.setHeader(ProtocolConstants.HEADER_LOCATION, result.getString(ProtocolConstants.KEY_LOCATION));
		response.setStatus(HttpServletResponse.SC_CREATED);
		return true;
	}

	private boolean fetch(HttpServletRequest request, HttpServletResponse response, GitCredentialsProvider cp, String path, boolean force) throws URISyntaxException, JSONException, IOException {
		// {remote}/{branch}/{file}/{path}
		Path p = new Path(path);
		FetchJob job = new FetchJob(TaskServlet.getUserId(request), cp, p, force);
		job.schedule();

		TaskInfo task = job.getTask();
		JSONObject result = task.toJSON();
		URI taskLocation = createTaskLocation(OrionServlet.getURI(request), task.getTaskId());
		result.put(ProtocolConstants.KEY_LOCATION, taskLocation);
		response.setHeader(ProtocolConstants.HEADER_LOCATION, taskLocation.toString());
		OrionServlet.writeJSONResponse(request, response, result);
		response.setStatus(HttpServletResponse.SC_ACCEPTED);
		return true;
	}

	private boolean pull(HttpServletRequest request, HttpServletResponse response, GitCredentialsProvider cp, String path, boolean force) throws URISyntaxException, JSONException, IOException {
		// {remote}/{branch}/{file}/{path}
		Path p = new Path(path);
		PullJob job = new PullJob(TaskServlet.getUserId(request), cp, p, force);
		job.schedule();

		TaskInfo task = job.getTask();
		JSONObject result = task.toJSON();
		URI taskLocation = createTaskLocation(OrionServlet.getURI(request), task.getTaskId());
		result.put(ProtocolConstants.KEY_LOCATION, taskLocation);
		response.setHeader(ProtocolConstants.HEADER_LOCATION, taskLocation.toString());
		OrionServlet.writeJSONResponse(request, response, result);
		response.setStatus(HttpServletResponse.SC_ACCEPTED);
		return true;
	}

	private boolean push(HttpServletRequest request, HttpServletResponse response, String path, GitCredentialsProvider cp, String srcRef, boolean tags, boolean force) throws ServletException, CoreException, IOException, JSONException, URISyntaxException {
		Path p = new Path(path);
		// FIXME: what if a remote or branch is named "file"?
		if (p.segment(2).equals("file")) { //$NON-NLS-1$
			// /git/remote/{remote}/{branch}/file/{path}
			PushJob job = new PushJob(TaskServlet.getUserId(request), cp, p, srcRef, tags, force);
			job.schedule();

			TaskInfo task = job.getTask();
			JSONObject result = task.toJSON();
			URI taskLocation = createTaskLocation(OrionServlet.getURI(request), task.getTaskId());
			result.put(ProtocolConstants.KEY_LOCATION, taskLocation);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, taskLocation.toString());
			OrionServlet.writeJSONResponse(request, response, result);
			response.setStatus(HttpServletResponse.SC_ACCEPTED);
			return true;
		}
		return false;
	}

	private URI createTaskLocation(URI baseLocation, String taskId) throws URISyntaxException {
		return new URI(baseLocation.getScheme(), baseLocation.getAuthority(), "/task/id/" + taskId, null, null); //$NON-NLS-1$
	}
}
