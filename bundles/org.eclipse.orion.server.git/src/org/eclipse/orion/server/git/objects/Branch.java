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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.BaseToCommitConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.*;

public class Branch extends GitObject {

	private Ref ref;

	public Branch(URI cloneLocation, Repository db, Ref ref) {
		super(cloneLocation, db);
		this.ref = ref;
	}

	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		JSONObject result = new JSONObject();
		String shortName = Repository.shortenRefName(ref.getName());
		result.put(ProtocolConstants.KEY_NAME, shortName);
		result.put(ProtocolConstants.KEY_TYPE, GitConstants.KEY_BRANCH_NAME);

		IPath basePath = new Path(cloneLocation.getPath());
		IPath newPath = new Path(GitServlet.GIT_URI).append(GitConstants.BRANCH_RESOURCE).append(shortName).append(basePath.removeFirstSegments(2));
		URI location = new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), newPath.toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
		result.put(ProtocolConstants.KEY_LOCATION, location);

		// add Git Clone URI
		result.put(GitConstants.KEY_CLONE, cloneLocation);

		// add Git Commit URI
		result.put(GitConstants.KEY_COMMIT, BaseToCommitConverter.getCommitLocation(cloneLocation, shortName, BaseToCommitConverter.REMOVE_FIRST_2));

		result.put(GitConstants.KEY_REMOTE, getRemotes());
		result.put(GitConstants.KEY_HEAD, BaseToCommitConverter.getCommitLocation(cloneLocation, Constants.HEAD, BaseToCommitConverter.REMOVE_FIRST_2));
		result.put(GitConstants.KEY_BRANCH_CURRENT, shortName.equals(db.getBranch()));

		return result;
	}

	private JSONArray getRemotes() throws URISyntaxException, JSONException, IOException, CoreException {
		String branchName = Repository.shortenRefName(ref.getName());
		JSONArray result = new JSONArray();
		Config config = db.getConfig();
		String remote = config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_REMOTE);
		if (remote != null) {
			RemoteConfig remoteConfig = new RemoteConfig(config, remote);
			if (!remoteConfig.getFetchRefSpecs().isEmpty()) {
				result.put(new Remote(cloneLocation, db, remote).toJSON(branchName));
			}
		} else {
			List<RemoteConfig> remoteConfigs = RemoteConfig.getAllRemoteConfigs(config);
			for (RemoteConfig remoteConfig : remoteConfigs) {
				if (!remoteConfig.getFetchRefSpecs().isEmpty()) {
					result.put(new Remote(cloneLocation, db, remoteConfig.getName()).toJSON(branchName));
				}
			}
		}
		return result;
	}

	@Override
	public String toString() {
		return "Branch [ref=" + ref + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
