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
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.BaseToRemoteConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Remote.TYPE)
public class Remote extends GitObject {

	public static final String RESOURCE = "remote"; //$NON-NLS-1$
	public static final String TYPE = "Remote"; //$NON-NLS-1$

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE_WITHOUT_CHILDREN = new ResourceShape();
	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	static {
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_LOCATION), // super
				new Property(GitConstants.KEY_CLONE), // super
				new Property(ProtocolConstants.KEY_NAME), //
				new Property(GitConstants.KEY_URL), //
				new Property(GitConstants.KEY_PUSH_URL),//
				new Property(GitConstants.KEY_IS_GERRIT) };
		DEFAULT_RESOURCE_SHAPE_WITHOUT_CHILDREN.setProperties(defaultProperties);

		DEFAULT_RESOURCE_SHAPE.setProperties(DEFAULT_RESOURCE_SHAPE_WITHOUT_CHILDREN.getProperties());
		DEFAULT_RESOURCE_SHAPE.addProperty(new Property(ProtocolConstants.KEY_CHILDREN));
	}
	private String name;
	private String newBranch;

	public Remote(URI cloneLocation, Repository db, String name) {
		super(cloneLocation, db);
		this.name = name;
	}

	public void setNewBranch(String newBranch) {
		this.newBranch = newBranch;
	}

	/**
	 * Returns a JSON representation of this remote.
	 */
	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		return toJSON(true);
	}

	public JSONObject toJSON(boolean includeChildren) throws JSONException, URISyntaxException, IOException, CoreException {
		Assert.isLegal(getConfig().getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION).contains(name), NLS.bind("Remote {0} not found.", name));
		if (includeChildren)
			return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
		else
			return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE_WITHOUT_CHILDREN);
	}

	@PropertyDescription(name = GitConstants.KEY_URL)
	private String getUrl() {
		return getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, name, "url" /* RemoteConfig.KEY_URL */); //$NON-NLS-1$
	}

	@PropertyDescription(name = GitConstants.KEY_PUSH_URL)
	private String getPushUrl() {
		return getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, name, "pushurl" /* RemoteConfig.KEY_PUSHURL */); //$NON-NLS-1$
	}

	@PropertyDescription(name = ProtocolConstants.KEY_CHILDREN)
	private JSONArray getChildren() throws IOException, JSONException, URISyntaxException, CoreException {
		JSONArray children = new JSONArray();
		boolean branchFound = false;
		List<Ref> refs = new ArrayList<Ref>();
		String currentBranch = db.getBranch();
		for (Entry<String, Ref> refEntry : db.getRefDatabase().getRefs(Constants.R_REMOTES + name + "/").entrySet()) { //$NON-NLS-1$
			if (!refEntry.getValue().isSymbolic()) {
				Ref ref = refEntry.getValue();
				String name = ref.getName();
				name = Repository.shortenRefName(name).substring(Constants.DEFAULT_REMOTE_NAME.length() + 1);
				if (currentBranch.equals(name)) {
					refs.add(0, ref);
				} else {
					refs.add(ref);
				}
			}
		}

		if (newBranch != null && !newBranch.isEmpty()) {
			for (Ref ref : refs) {
				String remoteBranchName = Repository.shortenRefName(ref.getName());
				remoteBranchName = remoteBranchName.substring((this.name + "/").length()); //$NON-NLS-1$
				if (remoteBranchName.equals(newBranch)) {
					RemoteBranch remoteBranch = new RemoteBranch(cloneLocation, db, this, remoteBranchName, ref);
					children = new JSONArray().put(remoteBranch.toJSON());
					branchFound = true;
					break;
				}
			}
		} else {
			for (Ref ref : refs) {
				String remoteBranchName = Repository.shortenRefName(ref.getName());
				remoteBranchName = remoteBranchName.substring((this.name + "/").length()); //$NON-NLS-1$
				RemoteBranch remoteBranch = new RemoteBranch(cloneLocation, db, this, remoteBranchName, ref);
				children.put(remoteBranch.toJSON());
			}
		}

		if (!branchFound && newBranch != null && !newBranch.isEmpty()) {
			JSONObject o = new JSONObject();
			// TODO: this should be a RemoteBranch
			boolean gerrit = getIsGerrit();
			String remoteName = getName();
			String fullName = gerrit ? "refs/for/" + newBranch : Constants.R_REMOTES + remoteName + "/" + newBranch; //$NON-NLS-1$ //$NON-NLS-2$
			String name = gerrit ? getName() + "/for/" + newBranch : remoteName + "/" + newBranch; //$NON-NLS-1$ //$NON-NLS-2$
			o.put(ProtocolConstants.KEY_NAME, name);
			o.put(ProtocolConstants.KEY_FULL_NAME, fullName);
			o.put(ProtocolConstants.KEY_TYPE, RemoteBranch.TYPE);
			o.put(GitConstants.KEY_URL, getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, remoteName, "url" /* RemoteConfig.KEY_URL */)); //$NON-NLS-1$
			o.put(ProtocolConstants.KEY_LOCATION, BaseToRemoteConverter.REMOVE_FIRST_2.baseToRemoteLocation(cloneLocation,
					"", /* short name is {remote}/{branch} */GitUtils.encode(remoteName) + "/" + GitUtils.encode(gerrit ? fullName : newBranch))); //$NON-NLS-1$
			children.put(o);
		}
		return children;
	}

	@Override
	public URI getLocation() throws URISyntaxException {
		return BaseToRemoteConverter.REMOVE_FIRST_2.baseToRemoteLocation(cloneLocation, GitUtils.encode(name), "" /* no branch name */); //$NON-NLS-1$
	}

	@PropertyDescription(name = ProtocolConstants.KEY_NAME)
	public String getName() {
		return name;
	}

	@PropertyDescription(name = GitConstants.KEY_IS_GERRIT)
	public boolean getIsGerrit() {
		return GitUtils.isGerrit(getConfig(), this.name);
	}

	@Override
	public String toString() {
		return "Remote [name=" + name + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
