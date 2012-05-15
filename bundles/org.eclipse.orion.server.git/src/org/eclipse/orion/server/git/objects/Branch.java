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
import java.util.Comparator;
import java.util.List;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.BaseToCommitConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.json.*;

@ResourceDescription(type = "Branch")
public class Branch extends GitObject {

	public static final String RESOURCE = "branch"; //$NON-NLS-1$
	public static final String TYPE = "Branch"; //$NON-NLS-1$
	public static final Comparator<Branch> COMPARATOR = new Comparator<Branch>() {
		public int compare(Branch o1, Branch o2) {
			return o1.getTime() < o2.getTime() ? 1 : (o1.getTime() > o2.getTime() ? -1 : o2.getName(true, false).compareTo(o1.getName(true, false)));
		}
	};

	private Ref ref;

	public Branch(URI cloneLocation, Repository db, Ref ref) {
		super(cloneLocation, db);
		this.ref = ref;
	}

	/**
	 * Returns a JSON representation of this local branch.
	 */
	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		JSONObject result = super.toJSON();
		result.put(ProtocolConstants.KEY_NAME, getName(false, false));
		result.put(ProtocolConstants.KEY_FULL_NAME, getName(true, false));
		result.put(GitConstants.KEY_COMMIT, BaseToCommitConverter.getCommitLocation(cloneLocation, getName(true, true), BaseToCommitConverter.REMOVE_FIRST_2));
		result.put(GitConstants.KEY_DIFF, createLocation(Diff.RESOURCE));
		result.put(GitConstants.KEY_REMOTE, getRemotes());
		result.put(GitConstants.KEY_HEAD, BaseToCommitConverter.getCommitLocation(cloneLocation, Constants.HEAD, BaseToCommitConverter.REMOVE_FIRST_2));
		result.put(GitConstants.KEY_BRANCH_CURRENT, isCurrent());
		result.put(ProtocolConstants.KEY_LOCAL_TIMESTAMP, (long) getTime() * 1000);
		return result;
	}

	private boolean isCurrent() throws IOException {
		return getName(false, false).equals(db.getBranch());
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return createLocation(Branch.RESOURCE);
	}

	private URI createLocation(String resource) throws URISyntaxException {
		String shortName = getName(false, true);
		IPath basePath = new Path(cloneLocation.getPath());
		IPath newPath = new Path(GitServlet.GIT_URI).append(resource).append(shortName).append(basePath.removeFirstSegments(2));
		return new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), newPath.toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
	}

	public JSONObject toJSON(JSONObject log) throws JSONException, URISyntaxException, IOException, CoreException {
		JSONObject result = this.toJSON();
		result.put(GitConstants.KEY_TAG_COMMIT, log);
		return result;
	}

	private JSONArray getRemotes() throws URISyntaxException, JSONException, IOException, CoreException {
		String branchName = Repository.shortenRefName(ref.getName());
		JSONArray result = new JSONArray();
		String remote = getConfig().getString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_REMOTE);
		if (remote != null) {
			RemoteConfig remoteConfig = new RemoteConfig(getConfig(), remote);
			if (!remoteConfig.getFetchRefSpecs().isEmpty()) {
				result.put(new Remote(cloneLocation, db, remote).toJSON(branchName));
			}
		} else {
			List<RemoteConfig> remoteConfigs = RemoteConfig.getAllRemoteConfigs(getConfig());
			for (RemoteConfig remoteConfig : remoteConfigs) {
				if (!remoteConfig.getFetchRefSpecs().isEmpty()) {
					Remote r = new Remote(cloneLocation, db, remoteConfig.getName());
					if (db.resolve(Constants.R_REMOTES + remoteConfig.getName() + "/" + branchName) != null) { //$NON-NLS-1$
						// it's an existing branch, not a new one, use it as filter
						return new JSONArray().put(r.toJSON(branchName));
					}
					result.put(r.toJSON(branchName));
				}
			}
		}
		return result;
	}

	public String getName(boolean fullName, boolean encode) {
		String name = ref.getName();
		if (!fullName)
			name = Repository.shortenRefName(ref.getName());
		if (encode)
			name = GitUtils.encode(name);
		return name;
	}

	public int getTime() {
		RevCommit c = parseCommit();
		if (c != null)
			return c.getCommitTime();
		return 0;
	}

	private RevCommit parseCommit() {
		ObjectId oid = ref.getObjectId();
		if (oid == null)
			return null;
		RevWalk walk = new RevWalk(db);
		try {
			return walk.parseCommit(oid);
		} catch (IOException e) {
			// ignore and return null
		} finally {
			walk.release();
		}
		return null;
	}

	@Override
	public String toString() {
		return "Branch [ref=" + ref + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	protected String getType() {
		return TYPE;
	}
}
