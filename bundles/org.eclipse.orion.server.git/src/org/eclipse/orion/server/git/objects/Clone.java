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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.JSONSerializer;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.Serializer;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.json.JSONArray;
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
	private URI baseLocation;
	private String cloneUrl;
	private IPath path;
	private JSONArray parents;

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
				new Property(GitConstants.KEY_STASH), //
				new Property(GitConstants.KEY_STATUS), //
				new Property(GitConstants.KEY_DIFF), //
				new Property(GitConstants.KEY_URL), //
				new Property(ProtocolConstants.KEY_CHILDREN),//
				new Property(ProtocolConstants.KEY_PARENTS),//
				new Property(GitConstants.KEY_SUBMODULE)};
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}
	protected Serializer<JSONObject> jsonSerializer = new JSONSerializer();

	/**
	 * Sets the clone id. The clone id is the HTTP resource URI of the file resource. This id is of the form /file/{workspaceId}/{projectName}[/{folderPath}]
	 * 
	 * @param id
	 */
	public void setId(String id) {
		this.id = id;
	}

	private String getId() {
		return this.id;
	}

	/**
	 * Sets the location of the contents of this clone. The location is an absolute URI referencing to the filesystem.
	 */
	public void setContentLocation(URI contentURI) {
		this.contentLocation = contentURI;
	}

	/**
	 * Returns the location of the contents of this clone. The result is an absolute URI in the filesystem.
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
		IPath np = new Path(GitServlet.GIT_URI).append(Clone.RESOURCE).append(getId());
		return createUriWithPath(np);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_CONTENT_LOCATION)
	private URI getContentLocation2() throws URISyntaxException { // TODO duplicated method
		IPath np = new Path(getId()).makeAbsolute();
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_REMOTE)
	private URI getRemoteLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Remote.RESOURCE).append(getId());
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_CONFIG)
	private URI getConfigLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(ConfigOption.RESOURCE).append(Clone.RESOURCE).append(getId());
		return createUriWithPath(np);
	}

	// TODO: expandable?
	@PropertyDescription(name = GitConstants.KEY_HEAD)
	private URI getHeadLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Commit.RESOURCE).append(Constants.HEAD).append(getId());
		return createUriWithPath(np);
	}
	
	// TODO: expandable?
	@PropertyDescription(name = GitConstants.KEY_SUBMODULE)
	private URI getSubmoduleLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Submodule.RESOURCE).append(getId());
		return createUriWithPath(np);
	}

	// TODO: expandable?
	@PropertyDescription(name = GitConstants.KEY_COMMIT)
	private URI getCommitLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Commit.RESOURCE).append(getId());
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_BRANCH)
	private URI getBranchLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Branch.RESOURCE).append(getId());
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_TAG)
	private URI getTagLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Tag.RESOURCE).append(getId());
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_INDEX)
	private URI getIndexLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Index.RESOURCE).append(getId());
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_STASH)
	private URI getIgnoreLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Stash.RESOURCE).append(getId());
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_STATUS)
	private URI getStatusLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Status.RESOURCE).append(getId());
		return createUriWithPath(np);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_DIFF)
	private URI getDiffLocation() throws URISyntaxException {
		IPath np = new Path(GitServlet.GIT_URI).append(Diff.RESOURCE).append(GitConstants.KEY_DIFF_DEFAULT).append(getId());
		return createUriWithPath(np);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_CHILDREN)
	private JSONArray getChildren() throws URISyntaxException, IOException, CoreException, JSONException {
		if (path == null)
			return null;
		IFileStore fileStore = NewFileServlet.getFileStore(null, path);
		if (fileStore == null)
			return null;
		File localFile = fileStore.toLocalFile(EFS.NONE, null);
		File gitModule = new File(localFile, ".gitmodules");
		JSONArray submodules = null;
		if (gitModule.exists() && !gitModule.isDirectory()) {
			submodules = new JSONArray();
			Repository parentRepository = null;
			try {
				parentRepository = FileRepositoryBuilder.create(GitUtils.resolveGitDir(localFile));
				SubmoduleWalk walk = SubmoduleWalk.forIndex(parentRepository);
				while (walk.next()) {
					String cloneUrl;
					if (walk.getRepository() != null) {
						cloneUrl = GitUtils.getCloneUrl(walk.getRepository());
					} else {
						cloneUrl = walk.getRemoteUrl();
					}
					JSONArray newParents = (this.parents == null ? new JSONArray() : new JSONArray(this.parents.toString()));
					newParents.put(getLocation().getPath());
					JSONObject submoduleCloneJSON = new Clone().toJSON(path.append(walk.getPath()).addTrailingSeparator(), baseLocation, cloneUrl, newParents);
					submodules.put(submoduleCloneJSON);
				}
				walk.close();
				submodules = submodules.length() > 0 ? submodules : null;
			} catch (ConfigInvalidException e) {
			} catch (IOException e) {
				// ignore and skip Git URL
			} finally {
				if (parentRepository != null) {
					parentRepository.close();
				}
			}

		}
		return submodules;
	}
	
	@PropertyDescription(name = ProtocolConstants.KEY_PARENTS)
	private JSONArray getParents() throws URISyntaxException, IOException, CoreException {
		return this.parents;
	} 
	
	@PropertyDescription(name = GitConstants.KEY_URL)
	private String getCloneUrl() {
		return cloneUrl;
	}

	private URI createUriWithPath(final IPath path) throws URISyntaxException {
		return new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), path.toString(),
				baseLocation.getQuery(), baseLocation.getFragment());
	}
	
	public JSONObject toJSON(IPath path, URI baseLocation, String cloneUrl) throws IOException, URISyntaxException {
		return toJSON(path, baseLocation, cloneUrl, null);
	}

	public JSONObject toJSON(IPath path, URI baseLocation, String cloneUrl, JSONArray parents) throws IOException, URISyntaxException {
		id = Activator.LOCATION_FILE_SERVLET + '/' + path.toString();
		name = path.lastSegment();
		this.path = path;
		this.cloneUrl = cloneUrl;
		this.baseLocation = baseLocation;
		this.parents = parents;
		return toJSON();
	} 
}
