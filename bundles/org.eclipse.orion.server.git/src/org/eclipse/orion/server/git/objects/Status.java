/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.objects;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.*;

@ResourceDescription(type = "Status")
public class Status extends GitObject {

	public static final String RESOURCE = "status"; //$NON-NLS-1$
	public static final String TYPE = "Status"; //$NON-NLS-1$

	private URI baseLocation;
	private org.eclipse.jgit.api.Status status;
	private IPath basePath;

	public Status(URI baseLocation, Repository db, org.eclipse.jgit.api.Status status, IPath basePath) throws URISyntaxException, CoreException {
		super(BaseToCloneConverter.getCloneLocation(baseLocation, BaseToCloneConverter.STATUS), db);
		this.baseLocation = baseLocation;
		this.status = status;
		this.basePath = basePath;
	}

	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		JSONObject result = super.toJSON();

		JSONArray children = toJSONArray(status.getAdded(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
		result.put(GitConstants.KEY_STATUS_ADDED, children);
		// TODO: bug 338913
		// children = toJSONArray(diff.getAssumeUnchanged(), baseLocation, ?);
		// result.put(GitConstants.KEY_STATUS_ASSUME_UNCHANGED, children);
		children = toJSONArray(status.getChanged(), basePath, baseLocation, GitConstants.KEY_DIFF_CACHED);
		result.put(GitConstants.KEY_STATUS_CHANGED, children);
		children = toJSONArray(status.getMissing(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
		result.put(GitConstants.KEY_STATUS_MISSING, children);
		children = toJSONArray(status.getModified(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
		result.put(GitConstants.KEY_STATUS_MODIFIED, children);
		children = toJSONArray(status.getRemoved(), basePath, baseLocation, GitConstants.KEY_DIFF_CACHED);
		result.put(GitConstants.KEY_STATUS_REMOVED, children);
		children = toJSONArray(status.getUntracked(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
		result.put(GitConstants.KEY_STATUS_UNTRACKED, children);
		children = toJSONArray(status.getConflicting(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
		result.put(GitConstants.KEY_STATUS_CONFLICTING, children);

		// return repository state
		result.put(GitConstants.KEY_REPOSITORY_STATE, db.getRepositoryState().name());

		result.put(GitConstants.KEY_INDEX, statusToIndexLocation(baseLocation));
		result.put(GitConstants.KEY_COMMIT, statusToCommitLocation(baseLocation, Constants.HEAD));

		return result;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return baseLocation;
	}

	private JSONArray toJSONArray(Set<String> set, IPath basePath, URI baseLocation, String diffType) throws JSONException, URISyntaxException {
		JSONArray result = new JSONArray();
		for (String s : set) {
			JSONObject object = new JSONObject();

			object.put(ProtocolConstants.KEY_NAME, s);
			IPath relative = new Path(s).makeRelativeTo(basePath);
			object.put(ProtocolConstants.KEY_PATH, relative);
			URI fileLocation = statusToFileLocation(baseLocation);
			object.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(fileLocation, relative.toString()));

			JSONObject gitSection = new JSONObject();
			URI diffLocation = statusToDiffLocation(baseLocation, diffType);
			gitSection.put(GitConstants.KEY_DIFF, URIUtil.append(diffLocation, relative.toString()));
			object.put(GitConstants.KEY_GIT, gitSection);

			URI commitLocation = statusToCommitLocation(baseLocation, Constants.HEAD);
			gitSection.put(GitConstants.KEY_COMMIT, URIUtil.append(commitLocation, relative.toString()));
			object.put(GitConstants.KEY_GIT, gitSection);

			URI indexLocation = statusToIndexLocation(baseLocation);
			gitSection.put(GitConstants.KEY_INDEX, URIUtil.append(indexLocation, relative.toString()));
			object.put(GitConstants.KEY_GIT, gitSection);

			result.put(object);
		}
		return result;
	}

	private URI statusToFileLocation(URI u) throws URISyntaxException {
		String uriPath = u.getPath();
		String prefix = uriPath.substring(0, uriPath.indexOf(GitServlet.GIT_URI));
		uriPath = uriPath.substring(prefix.length() + (GitServlet.GIT_URI + '/' + Status.RESOURCE).length());
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), uriPath, u.getQuery(), u.getFragment());
	}

	private URI statusToDiffLocation(URI u, String diffType) throws URISyntaxException {
		String uriPath = u.getPath();
		String prefix = uriPath.substring(0, uriPath.indexOf(GitServlet.GIT_URI));
		uriPath = uriPath.substring(prefix.length() + (GitServlet.GIT_URI + '/' + Status.RESOURCE).length());
		uriPath = prefix + GitServlet.GIT_URI + '/' + Diff.RESOURCE + '/' + diffType + uriPath;
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), uriPath, u.getQuery(), u.getFragment());
	}

	private URI statusToCommitLocation(URI u, String ref) throws URISyntaxException {
		String uriPath = u.getPath();
		String prefix = uriPath.substring(0, uriPath.indexOf(GitServlet.GIT_URI));
		uriPath = uriPath.substring(prefix.length() + (GitServlet.GIT_URI + '/' + Status.RESOURCE).length());
		uriPath = prefix + GitServlet.GIT_URI + '/' + Commit.RESOURCE + '/' + ref + uriPath;
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), uriPath, u.getQuery(), u.getFragment());
	}

	private URI statusToIndexLocation(URI u) throws URISyntaxException {
		String uriPath = u.getPath();
		String prefix = uriPath.substring(0, uriPath.indexOf(GitServlet.GIT_URI));
		uriPath = uriPath.substring(prefix.length() + (GitServlet.GIT_URI + '/' + Status.RESOURCE).length());
		uriPath = prefix + GitServlet.GIT_URI + '/' + Index.RESOURCE + uriPath;
		return new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), uriPath, u.getQuery(), u.getFragment());
	}

	@Override
	protected String getType() {
		return TYPE;
	}
}
