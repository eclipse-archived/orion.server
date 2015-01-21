/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Status.TYPE)
public class Status extends GitObject {

	public static final String RESOURCE = "status"; //$NON-NLS-1$
	public static final String TYPE = "Status"; //$NON-NLS-1$

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_LOCATION), // super
				new Property(GitConstants.KEY_CLONE), // super
				new Property(GitConstants.KEY_STATUS_ADDED), //
				// TODO: assume unchanged, bug 338913
				new Property(GitConstants.KEY_STATUS_CHANGED), //
				new Property(GitConstants.KEY_STATUS_MISSING), //
				new Property(GitConstants.KEY_STATUS_MODIFIED), //
				new Property(GitConstants.KEY_STATUS_REMOVED), //
				new Property(GitConstants.KEY_STATUS_UNTRACKED), //
				new Property(GitConstants.KEY_STATUS_CONFLICTING), //
				new Property(GitConstants.KEY_REPOSITORY_STATE), //
				new Property(GitConstants.KEY_INDEX), //
				new Property(GitConstants.KEY_COMMIT) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

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
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@PropertyDescription(name = GitConstants.KEY_STATUS_ADDED)
	private JSONArray getAdded() throws JSONException, URISyntaxException {
		return toJSONArray(status.getAdded(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
	}

	@PropertyDescription(name = GitConstants.KEY_STATUS_CHANGED)
	private JSONArray getChanged() throws JSONException, URISyntaxException {
		return toJSONArray(status.getChanged(), basePath, baseLocation, GitConstants.KEY_DIFF_CACHED);
	}

	@PropertyDescription(name = GitConstants.KEY_STATUS_MISSING)
	private JSONArray getMissing() throws JSONException, URISyntaxException {
		return toJSONArray(status.getMissing(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
	}

	@PropertyDescription(name = GitConstants.KEY_STATUS_MODIFIED)
	private JSONArray getModified() throws JSONException, URISyntaxException {
		return toJSONArray(status.getModified(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
	}

	@PropertyDescription(name = GitConstants.KEY_STATUS_REMOVED)
	private JSONArray getRemoved() throws JSONException, URISyntaxException {
		return toJSONArray(status.getRemoved(), basePath, baseLocation, GitConstants.KEY_DIFF_CACHED);
	}

	@PropertyDescription(name = GitConstants.KEY_STATUS_UNTRACKED)
	private JSONArray getUntracked() throws JSONException, URISyntaxException {
		return toJSONArray(status.getUntracked(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
	}

	@PropertyDescription(name = GitConstants.KEY_STATUS_CONFLICTING)
	private JSONArray getConflicting() throws JSONException, URISyntaxException {
		return toJSONArray(status.getConflicting(), basePath, baseLocation, GitConstants.KEY_DIFF_DEFAULT);
	}

	@PropertyDescription(name = GitConstants.KEY_REPOSITORY_STATE)
	private String getRepositoryState() {
		return db.getRepositoryState().name();
	}

	@PropertyDescription(name = GitConstants.KEY_INDEX)
	private URI getIndexLocation() throws URISyntaxException {
		return statusToIndexLocation(baseLocation);
	}

	@PropertyDescription(name = GitConstants.KEY_COMMIT)
	private URI getCommitLocation() throws URISyntaxException {
		return statusToCommitLocation(baseLocation, Constants.HEAD);
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return baseLocation;
	}

	private JSONArray toJSONArray(Set<String> set, IPath basePath, URI baseLocation, String diffType) throws JSONException, URISyntaxException {
		JSONArray result = new JSONArray();
		for (String s : set) {
			if (s.indexOf(':') >= 0) {
				s = s.replace(":", "%3A");
			}
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
}
