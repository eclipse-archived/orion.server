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
import org.eclipse.orion.server.core.resources.*;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A git clone created in Orion.
 */
@ResourceDescription(type = Clone.TYPE)
public class Clone {

	public static final String RESOURCE = "clone"; //$NON-NLS-1$
	public static final String TYPE = "Clone"; //$NON-NLS-1$

	private String id;
	private URI contentLocation;
	private URIish uriish;
	private String name;
	private FileRepository db;
	private URI baseLocation;

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_NAME), //
				new Property(ProtocolConstants.KEY_LOCATION), //
				new Property(ProtocolConstants.KEY_CONTENT_LOCATION), //
				new Property(GitConstants.KEY_REMOTE), //
				new Property(GitConstants.KEY_CONFIG), //
				new Property(GitConstants.KEY_HEAD), //
				new Property(GitConstants.KEY_COMMIT), //
				new Property(GitConstants.KEY_BRANCH), //
				new Property(GitConstants.KEY_TAG), //
				new Property(GitConstants.KEY_INDEX), //
				new Property(GitConstants.KEY_STATUS), //
				new Property(GitConstants.KEY_DIFF), //
				new Property(GitConstants.KEY_URL)};
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}
	protected Serializer<JSONObject> jsonSerializer = new JSONSerializer();

	public void setId(String id) {
		this.id = id;
	}

	private String getId() {
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

	@PropertyDescription(name = ProtocolConstants.KEY_NAME)
	public String getName() {
		return this.name;
	}

	private FileRepository getRepository() throws IOException {
		if (db == null)
			db = new FileRepository(new File(new File(getContentLocation()), Constants.DOT_GIT));
		return db;
	}

	public void setBaseLocation(URI baseLocation) {
		this.baseLocation = baseLocation;
	}

	/**
	 * Returns a JSON representation of this clone.
	 * 
	 * @return
	 * @throws JSONException
	 * @throws URISyntaxException
	 */
	public JSONObject toJSON() throws URISyntaxException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_LOCATION)
	private URI getLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Clone.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
		return createUriWithPath(np);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_CONTENT_LOCATION)
	private URI getContentLocation2() throws URISyntaxException { // TODO duplicated method
		IPath np = new Path("file").append(getId()).makeAbsolute(); //$NON-NLS-1$
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_REMOTE)
	private URI getRemoteLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Remote.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_CONFIG)
	private URI getConfigLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(ConfigOption.RESOURCE).append(Clone.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
		return createUriWithPath(np);
	}

	// TODO: expandable?
	@PropertyDescription(name = GitConstants.KEY_HEAD)
	private URI getHeadLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Commit.RESOURCE).append(Constants.HEAD).append("file").append(getId()); //$NON-NLS-1$
		return createUriWithPath(np);
	}

	// TODO: expandable?
	@PropertyDescription(name = GitConstants.KEY_COMMIT)
	private URI getCommitLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Commit.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_BRANCH)
	private URI getBranchLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Branch.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_TAG)
	private URI getTagLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Tag.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_INDEX)
	private URI getIndexLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Index.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_STATUS)
	private URI getStatusLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Status.RESOURCE).append("file").append(getId()); //$NON-NLS-1$
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_DIFF)
	private URI getDiffLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Diff.RESOURCE).append(GitConstants.KEY_DIFF_DEFAULT).append("file").append(getId()); //$NON-NLS-1$
		return createUriWithPath(np);
	}

	@PropertyDescription(name = GitConstants.KEY_URL)
	private String getCloneUrl() {
		try {
			FileBasedConfig config = getRepository().getConfig();
			String remoteUri = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL);
			if (remoteUri != null)
				return remoteUri;
		} catch (IOException e) {
			// ignore and skip Git URL
		}
		return null;
	}

	private URI createUriWithPath(final IPath path) throws URISyntaxException {
		return new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), path.toString(), baseLocation.getQuery(), baseLocation.getFragment());
	}

	public JSONObject toJSON(Entry<IPath, File> entry, URI baseLocation) throws JSONException, IOException, URISyntaxException {
		id = entry.getKey().toString();
		name = entry.getKey().segmentCount() == 1 ? WebProject.fromId(entry.getKey().segment(0)).getName() : entry.getKey().lastSegment();
		db = new FileRepository(entry.getValue());
		this.baseLocation = baseLocation;
		return toJSON();
	}
}
