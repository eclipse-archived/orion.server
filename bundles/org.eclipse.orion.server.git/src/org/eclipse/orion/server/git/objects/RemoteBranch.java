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
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.lib.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.*;
import org.json.JSONException;
import org.json.JSONObject;

public class RemoteBranch extends GitObject {

	public static final String TYPE = "RemoteTrackingBranch"; //$NON-NLS-1$

	private Remote remote;
	private String name;

	public RemoteBranch(URI cloneLocation, Repository db, Remote remote, String name) {
		super(cloneLocation, db);
		this.remote = remote;
		this.name = name;
	}

	/**
	 * Returns a JSON representation of this remote branch.
	 */
	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		Set<String> configNames = getConfig().getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION);
		for (String configName : configNames) {
			if (configName.equals(remote.getName())) {
				final String fullName = Constants.R_REMOTES + remote.getName() + "/" + name; //$NON-NLS-1$
				Ref ref = db.getRefDatabase().getRef(fullName);
				if (ref != null && !ref.isSymbolic()) {
					JSONObject result = super.toJSON();
					result.put(ProtocolConstants.KEY_NAME, Repository.shortenRefName(fullName));
					result.put(ProtocolConstants.KEY_FULL_NAME, fullName);
					result.put(ProtocolConstants.KEY_ID, ref.getObjectId().name());
					// see bug 342602
					// result.put(GitConstants.KEY_COMMIT, baseToCommitLocation(baseLocation, name));
					result.put(GitConstants.KEY_COMMIT, BaseToCommitConverter.getCommitLocation(cloneLocation, ref.getObjectId().name(), BaseToCommitConverter.REMOVE_FIRST_2));
					result.put(GitConstants.KEY_HEAD, BaseToCommitConverter.getCommitLocation(cloneLocation, Constants.HEAD, BaseToCommitConverter.REMOVE_FIRST_2));
					// result.put(GitConstants.KEY_BRANCH, BaseToBranchConverter.getBranchLocation(cloneLocation, BaseToBranchConverter.REMOTE));
					result.put(GitConstants.KEY_INDEX, BaseToIndexConverter.getIndexLocation(cloneLocation, BaseToIndexConverter.CLONE));
					return result;
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

	@Override
	protected String getType() {
		return TYPE;
	}
}
