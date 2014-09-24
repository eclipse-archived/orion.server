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

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.BaseToCommitConverter;
import org.eclipse.orion.server.git.BaseToIndexConverter;
import org.eclipse.orion.server.git.BaseToRemoteConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = RemoteBranch.TYPE)
public class RemoteBranch extends GitObject {

	public static final String TYPE = "RemoteTrackingBranch"; //$NON-NLS-1$

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_LOCATION), // super
				new Property(GitConstants.KEY_CLONE), // super
				new Property(ProtocolConstants.KEY_NAME), //
				new Property(ProtocolConstants.KEY_FULL_NAME), //
				new Property(ProtocolConstants.KEY_ID), //
				new Property(GitConstants.KEY_COMMIT), //
				new Property(GitConstants.KEY_TREE), //
				new Property(GitConstants.KEY_HEAD), //
				new Property(GitConstants.KEY_INDEX), //
				new Property(GitConstants.KEY_URL), //
				new Property(GitConstants.KEY_DIFF) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	private Remote remote;
	private String name;
	private Ref ref;

	public RemoteBranch(URI cloneLocation, Repository db, Remote remote, String name) {
		super(cloneLocation, db);
		this.remote = remote;
		this.name = name;
		this.ref = findRef();
	}

	public RemoteBranch(URI cloneLocation, Repository db, Remote remote, String name, Ref ref) {
		super(cloneLocation, db);
		this.remote = remote;
		this.name = name;
		this.ref = ref;
	}

	private Ref findRef() {
		try {
			Set<String> configNames = getConfig().getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION);
			for (String configName : configNames) {
				if (configName.equals(remote.getName())) {
					final String fullName = getName(true, false);
					Ref ref = db.getRefDatabase().getRef(fullName);
					if (ref != null && !ref.isSymbolic()) {
						return ref;
					}
				}
			}
		} catch (IOException e) {
			// ignore, return null
		}
		return null;
	}

	/**
	 * Returns a JSON representation of this remote branch.
	 */
	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@PropertyDescription(name = GitConstants.KEY_DIFF)
	private URI getDiffLocation() throws URISyntaxException {
		Assert.isNotNull(cloneLocation);
		IPath basePath = new Path(cloneLocation.getPath());
		IPath p = new Path(GitServlet.GIT_URI).append(Diff.RESOURCE).append(getName(false, true)).append(basePath.removeFirstSegments(2));
		return new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), p.toString(),
				cloneLocation.getQuery(), cloneLocation.getFragment());
	}

	@PropertyDescription(name = ProtocolConstants.KEY_NAME)
	private String getName() {
		return getName(false, false);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_FULL_NAME)
	private String getFullName() {
		return getName(true, false);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_ID)
	private String getId() {
		return ref.getObjectId().name();
	}

	@PropertyDescription(name = GitConstants.KEY_URL)
	private String getUrl() {
		return getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, remote.getName(), "url" /* RemoteConfig.KEY_URL */); //$NON-NLS-1$
	}

	private String getName(boolean fullName, boolean encode) {
		String name = Constants.R_REMOTES + remote.getName() + "/" + this.name; //$NON-NLS-1$
		if (!fullName)
			name = Repository.shortenRefName(name);
		if (encode)
			name = GitUtils.encode(name);
		return name;
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_COMMIT)
	private URI getCommitLocation() throws IOException, URISyntaxException {
		return BaseToCommitConverter.getCommitLocation(cloneLocation, getName(true, true), BaseToCommitConverter.REMOVE_FIRST_2);
	}

	@PropertyDescription(name = GitConstants.KEY_TREE)
	private URI getTreeLocation() throws URISyntaxException {
		return createTreeLocation(null);
	}

	private URI createTreeLocation(String path) throws URISyntaxException {
		// remove /gitapi/clone from the start of path
		IPath clonePath = new Path(cloneLocation.getPath()).removeFirstSegments(2);

		IPath result = new Path(GitServlet.GIT_URI).append(Tree.RESOURCE).append(clonePath).append(GitUtils.encode(this.getName()));
		if (path != null) {
			result.append(path);
		}
		return new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), result.makeAbsolute()
				.toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
	}

	@PropertyDescription(name = GitConstants.KEY_HEAD)
	private URI getHeadLocation() throws IOException, URISyntaxException {
		return BaseToCommitConverter.getCommitLocation(cloneLocation, Constants.HEAD, BaseToCommitConverter.REMOVE_FIRST_2);
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_INDEX)
	private URI getIndexLocation() throws IOException, URISyntaxException {
		return BaseToIndexConverter.getIndexLocation(cloneLocation, BaseToIndexConverter.CLONE);
	}

	@Override
	public URI getLocation() throws URISyntaxException {
		// TODO bug 377090
		// Assert.isNotNull(cloneLocation);
		if (cloneLocation == null)
			return null;
		return BaseToRemoteConverter.REMOVE_FIRST_2.baseToRemoteLocation(cloneLocation, remote.getName(), GitUtils.encode(name));
	}

	@Override
	public String toString() {
		return "RemoteBranch [remote=" + remote + ", name=" + name + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	public boolean exists() {
		return ref != null;
	}

}
