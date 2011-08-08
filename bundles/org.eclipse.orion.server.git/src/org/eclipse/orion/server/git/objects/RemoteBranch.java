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
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.lib.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.*;
import org.json.JSONException;
import org.json.JSONObject;

public class RemoteBranch extends GitObject {

	private Remote remote;
	private String name;

	public RemoteBranch(URI cloneLocation, Repository db, Remote remote, String name) {
		super(cloneLocation, db);
		this.remote = remote;
		this.name = name;
	}

	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		Set<String> configNames = db.getConfig().getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION);
		for (String configName : configNames) {
			if (configName.equals(remote.getName())) {
				for (Entry<String, Ref> refEntry : db.getRefDatabase().getRefs(Constants.R_REMOTES).entrySet()) {
					Ref ref = refEntry.getValue();
					String refName = ref.getName();
					String fullName = Constants.R_REMOTES + remote.getName() + "/" + name; //$NON-NLS-1$
					if (!ref.isSymbolic() && refName.equals(fullName)) {
						JSONObject result = new JSONObject();
						result.put(ProtocolConstants.KEY_NAME, Repository.shortenRefName(fullName));
						result.put(ProtocolConstants.KEY_FULL_NAME, fullName);
						result.put(ProtocolConstants.KEY_TYPE, GitConstants.REMOTE_TRACKING_BRANCH_TYPE);
						result.put(ProtocolConstants.KEY_ID, ref.getObjectId().name());
						// see bug 342602
						// result.put(GitConstants.KEY_COMMIT, baseToCommitLocation(baseLocation, name));
						result.put(ProtocolConstants.KEY_LOCATION, getLocation());
						result.put(GitConstants.KEY_COMMIT, BaseToCommitConverter.getCommitLocation(cloneLocation, ref.getObjectId().name(), BaseToCommitConverter.REMOVE_FIRST_2));
						result.put(GitConstants.KEY_HEAD, BaseToCommitConverter.getCommitLocation(cloneLocation, Constants.HEAD, BaseToCommitConverter.REMOVE_FIRST_2));
						result.put(GitConstants.KEY_CLONE, cloneLocation);
						return result;
					}
				}
			}
		}
		return null;
	}

	public URI getLocation() throws URISyntaxException {
		if (cloneLocation == null)
			return null; // TODO: throw IllegalArgumentException
		return BaseToRemoteConverter.REMOVE_FIRST_2.baseToRemoteLocation(cloneLocation, remote.getName(), name);
	}

	@Override
	public String toString() {
		return "RemoteBranch [remote=" + remote + ", name=" + name + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
