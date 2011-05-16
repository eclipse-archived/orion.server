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
import java.util.*;
import java.util.Map.Entry;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
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
					// case PUT :
					// return handlePut(request, response, path);
				case POST :
					return handlePost(request, response, path);
					// case DELETE :
					// return handleDelete(request, response, path);
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
		if (p.segment(0).equals("file")) {
			// /git/remote/file/{path}
			File gitDir = GitUtils.getGitDir(p);
			Repository db = new FileRepository(gitDir);
			Set<String> configNames = db.getConfig().getSubsections("remote");
			JSONObject result = new JSONObject();
			JSONArray children = new JSONArray();
			URI baseLocation = getURI(request);
			for (String configName : configNames) {
				JSONObject o = new JSONObject();
				o.put(ProtocolConstants.KEY_NAME, configName);
				o.put(ProtocolConstants.KEY_LOCATION, baseToRemoteLocation(baseLocation, 2, configName));
				children.put(o);
			}
			result.put(ProtocolConstants.KEY_CHILDREN, children);
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} else if (p.segment(1).equals("file")) {
			// /git/remote/{remote}/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(1));
			Repository db = new FileRepository(gitDir);
			Set<String> configNames = db.getConfig().getSubsections("remote");
			JSONObject result = new JSONObject();
			URI baseLocation = getURI(request);
			for (String configName : configNames) {
				if (configName.equals(p.segment(0))) {
					result.put(ProtocolConstants.KEY_NAME, configName);
					result.put(ProtocolConstants.KEY_LOCATION, baseToRemoteLocation(baseLocation, 3, configName));

					JSONArray children = new JSONArray();
					List<Ref> refs = new ArrayList<Ref>();
					for (Entry<String, Ref> refEntry : db.getRefDatabase().getRefs(Constants.R_REMOTES).entrySet()) {
						if (!refEntry.getValue().isSymbolic()) {
							Ref ref = refEntry.getValue();
							String name = ref.getName();
							name = Repository.shortenRefName(name).substring(Constants.DEFAULT_REMOTE_NAME.length() + 1);
							if (db.getBranch().equals(name)) {
								refs.add(0, ref);
							} else {
								refs.add(ref);
							}
						}
					}
					for (Ref ref : refs) {
						JSONObject o = new JSONObject();
						String name = ref.getName();
						o.put(ProtocolConstants.KEY_NAME, name);
						o.put(ProtocolConstants.KEY_ID, ref.getObjectId().name());
						// see bug 342602
						// o.put(GitConstants.KEY_COMMIT, baseToCommitLocation(baseLocation, name));
						o.put(ProtocolConstants.KEY_LOCATION, baseToRemoteLocation(baseLocation, 3, Repository.shortenRefName(name)));
						o.put(GitConstants.KEY_COMMIT, baseToCommitLocation(baseLocation, 3, ref.getObjectId().name()));
						children.put(o);
					}
					result.put(ProtocolConstants.KEY_CHILDREN, children);
					OrionServlet.writeJSONResponse(request, response, result);
					return true;
				}
			}
			String msg = NLS.bind("Couldn't find remote : {0}", p.segment(0)); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null));
		} else if (p.segment(2).equals("file")) {
			// /git/remote/{remote}/{branch}/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
			Repository db = new FileRepository(gitDir);
			Set<String> configNames = db.getConfig().getSubsections("remote");
			URI baseLocation = getURI(request);
			for (String configName : configNames) {
				if (configName.equals(p.segment(0))) {
					for (Entry<String, Ref> refEntry : db.getRefDatabase().getRefs(Constants.R_REMOTES).entrySet()) {
						Ref ref = refEntry.getValue();
						String name = ref.getName();
						if (!ref.isSymbolic() && name.equals(Constants.R_REMOTES + p.uptoSegment(2).removeTrailingSeparator())) {
							JSONObject result = new JSONObject();
							result.put(ProtocolConstants.KEY_ID, ref.getObjectId().name());
							// see bug 342602
							// result.put(GitConstants.KEY_COMMIT, baseToCommitLocation(baseLocation, name));
							result.put(ProtocolConstants.KEY_LOCATION, baseToRemoteLocation(baseLocation, 4, Repository.shortenRefName(name)));
							result.put(GitConstants.KEY_COMMIT, baseToCommitLocation(baseLocation, 4, ref.getObjectId().name()));
							result.put(GitConstants.KEY_HEAD, baseToCommitLocation(baseLocation, 4, Constants.HEAD));
							OrionServlet.writeJSONResponse(request, response, result);
							return true;
						}
					}
				}
				// TODO: 404
			}
		}
		// TODO: 400
		return false;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		JSONObject requestObject = OrionServlet.readJSONRequest(request);
		boolean fetch = Boolean.parseBoolean(requestObject.optString(GitConstants.KEY_FETCH, null));
		String srcRef = requestObject.optString(GitConstants.KEY_PUSH_SRC_REF, null);
		boolean tags = requestObject.optBoolean(GitConstants.KEY_PUSH_TAGS, false);

		String username = requestObject.optString(GitConstants.KEY_USERNAME, null);
		char[] password = requestObject.optString(GitConstants.KEY_PASSWORD, "").toCharArray(); //$NON-NLS-1$
		String knownHosts = requestObject.optString(GitConstants.KEY_KNOWN_HOSTS, null);
		byte[] privateKey = requestObject.optString(GitConstants.KEY_PRIVATE_KEY, "").getBytes(); //$NON-NLS-1$
		byte[] publicKey = requestObject.optString(GitConstants.KEY_PUBLIC_KEY, "").getBytes(); //$NON-NLS-1$
		byte[] passphrase = requestObject.optString(GitConstants.KEY_PASSPHRASE, "").getBytes(); //$NON-NLS-1$

		// if all went well, push
		GitCredentialsProvider cp = new GitCredentialsProvider(null, username, password, knownHosts);
		cp.setPrivateKey(privateKey);
		cp.setPublicKey(publicKey);
		cp.setPassphrase(passphrase);

		if (fetch) {
			return fetch(request, response, cp, path);
		} else if (srcRef != null) {
			if (srcRef.equals("")) { //$NON-NLS-1$
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Pushing with an empty source ref is not allowed. Did you mean DELETE?", null));
			}
			return push(request, response, path, cp, srcRef, tags);
		} else {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Only Fetch:true is currently supported.", null));
		}
	}

	private boolean fetch(HttpServletRequest request, HttpServletResponse response, GitCredentialsProvider cp, String path) throws URISyntaxException, JSONException, IOException {
		// {remote}/{branch}/{file}/{path}
		Path p = new Path(path);
		FetchJob job = new FetchJob(cp, p);
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

	private boolean push(HttpServletRequest request, HttpServletResponse response, String path, GitCredentialsProvider cp, String srcRef, boolean tags) throws ServletException, CoreException, IOException, JSONException, URISyntaxException {
		Path p = new Path(path);
		// FIXME: what if a remote or branch is named "file"?
		if (p.segment(2).equals("file")) {
			// /git/remote/{remote}/{branch}/file/{path}
			PushJob job = new PushJob(cp, p, srcRef, tags);
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
		return new URI(baseLocation.getScheme(), baseLocation.getAuthority(), "/task/id/" + taskId, null, null);
	}

	public static URI baseToRemoteLocation(URI u, int count, String remoteName) throws URISyntaxException {
		// URIUtil.append(baseLocation, configName)
		IPath p = new Path(u.getPath());
		p = p.uptoSegment(2).append(remoteName).addTrailingSeparator().append(p.removeFirstSegments(count));
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), p.toString(), u.getQuery(), u.getFragment());
	}

	private URI baseToCommitLocation(URI u, int c, String ref) throws URISyntaxException {
		String uriPath = u.getPath();
		IPath path = new Path(uriPath);
		IPath filePath = path.removeFirstSegments(c).makeAbsolute();
		uriPath = GitServlet.GIT_URI + "/" + GitConstants.COMMIT_RESOURCE + "/" + ref + filePath.toString(); //$NON-NLS-1$ //$NON-NLS-2$
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), uriPath, u.getQuery(), u.getFragment());
	}
}
