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
package org.eclipse.orion.server.git.objects;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map.Entry;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A git clone created in Orion.
 */
public class Clone {

	public static final String RESOURCE = "clone"; //$NON-NLS-1$
	public static final String TYPE = "Clone"; //$NON-NLS-1$

	private String id;
	private URI contentLocation;
	private URIish uriish;
	private String name;
	private FileRepository db;

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return this.id;
	}

	/**
	 * Sets the location of the contents of this clone. The location is an
	 * absolute URI referencing to the filesystem.
	 */
	public void setContentLocation(URI contentURI) {
		this.contentLocation = contentURI;
	}

	/**
	 * Returns the location of the contents of this clone. The result is an
	 * absolute URI in the filesystem.
	 * 
	 * @return The location of the contents of this clone
	 */
	public URI getContentLocation() {
		return this.contentLocation;
	}

	/**
	 * Sets the git URL of this clone.
	 */
	public void setUrl(URIish gitURI) {
		this.uriish = gitURI;
	}

	/**
	 * Returns the URL of the git repository.
	 * <p>
	 * This method never returns null.
	 * </p>
	 * 
	 * @return The URL of the git repository
	 */
	public String getUrl() {
		return this.uriish.toString();
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	private FileRepository getRepository() throws IOException {
		if (db == null)
			db = new FileRepository(new File(new File(getContentLocation()), Constants.DOT_GIT));
		return db;
	}

	/**
	 * Returns a JSON representation of this clone.
	 * 
	 * @param baseLocation
	 * @return
	 * @throws JSONException
	 * @throws URISyntaxException
	 */
	public JSONObject toJSON(URI baseLocation) throws JSONException, URISyntaxException {
		JSONObject result = new JSONObject();
		try {
			result.put(ProtocolConstants.KEY_ID, getId());
			result.put(ProtocolConstants.KEY_NAME, getName());
			result.put(ProtocolConstants.KEY_TYPE, TYPE);

			IPath np = new Path(GitServlet.GIT_URI).append(Clone.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
			URI location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(ProtocolConstants.KEY_LOCATION, location);

			np = new Path("file").append(getId()).makeAbsolute(); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(ProtocolConstants.KEY_CONTENT_LOCATION, location);

			np = new Path(GitServlet.GIT_URI).append(Remote.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_REMOTE, location);

			np = new Path(GitServlet.GIT_URI).append(ConfigOption.RESOURCE).append(Clone.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_CONFIG, location);

			np = new Path(GitServlet.GIT_URI).append(Commit.RESOURCE).append(Constants.HEAD).append("file").append(getId()); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_HEAD, location);

			np = new Path(GitServlet.GIT_URI).append(Commit.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_COMMIT, location);

			np = new Path(GitServlet.GIT_URI).append(Branch.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_BRANCH, location);

			np = new Path(GitServlet.GIT_URI).append(Tag.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_TAG, location);

			np = new Path(GitServlet.GIT_URI).append(Index.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_INDEX, location);

			np = new Path(GitServlet.GIT_URI).append(Status.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_STATUS, location);

			np = new Path(GitServlet.GIT_URI).append(Diff.RESOURCE).append(GitConstants.KEY_DIFF_DEFAULT).append("file").append(getId()); //$NON-NLS-1$
			location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), np.toString(), baseLocation.getQuery(), baseLocation.getFragment());
			result.put(GitConstants.KEY_DIFF, location);

			try {
				FileBasedConfig config = getRepository().getConfig();
				String remoteUri = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL);
				if (remoteUri != null)
					result.put(GitConstants.KEY_URL, remoteUri);
			} catch (IOException e) {
				// ignore and skip Git URL
			}
		} catch (JSONException e) {
			//cannot happen, we know keys and values are valid
		}
		return result;
	}

	public JSONObject toJSON(Entry<IPath, File> entry, URI baseLocation) throws JSONException, IOException, URISyntaxException {
		id = entry.getKey().toString();
		name = entry.getKey().segmentCount() == 1 ? WebProject.fromId(entry.getKey().segment(0)).getName() : entry.getKey().lastSegment();
		db = new FileRepository(entry.getValue());
		return toJSON(baseLocation);
	}
}
