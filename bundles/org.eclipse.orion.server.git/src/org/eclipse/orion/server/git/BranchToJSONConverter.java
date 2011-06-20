/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Generates JSON representation of the given branch.
 */
public class BranchToJSONConverter {

	public static JSONObject toJSON(Ref ref, Repository db, URI baseLocation, int segmentsToRemove) throws JSONException, URISyntaxException, IOException, CoreException {
		JSONObject result = new JSONObject();
		String shortName = Repository.shortenRefName(ref.getName());
		result.put(ProtocolConstants.KEY_NAME, shortName);
		result.put(ProtocolConstants.KEY_TYPE, GitConstants.KEY_BRANCH_NAME);

		IPath basePath = new Path(baseLocation.getPath());
		IPath newPath = new Path(GitServlet.GIT_URI).append(GitConstants.BRANCH_RESOURCE).append(shortName).append(basePath.removeFirstSegments(segmentsToRemove));
		URI location = new URI(baseLocation.getScheme(), baseLocation.getUserInfo(), baseLocation.getHost(), baseLocation.getPort(), newPath.toString(), baseLocation.getQuery(), baseLocation.getFragment());
		result.put(ProtocolConstants.KEY_LOCATION, location);

		// add Git Clone URI
		result.put(GitConstants.KEY_CLONE, BaseToCloneConverter.getCloneLocation(location, BaseToCloneConverter.BRANCH));

		// add Git Commit URI
		result.put(GitConstants.KEY_COMMIT, BaseToCommitConverter.getCommitLocation(location, shortName, BaseToCommitConverter.REMOVE_FIRST_3));

		result.put(GitConstants.KEY_REMOTE, BaseToRemoteConverter.getRemoteBranchLocation(location, shortName, db, BaseToRemoteConverter.REMOVE_FIRST_3));
		result.put(GitConstants.KEY_HEAD, BaseToCommitConverter.getCommitLocation(location, Constants.HEAD, BaseToCommitConverter.REMOVE_FIRST_3));
		result.put(GitConstants.KEY_BRANCH_CURRENT, shortName.equals(db.getBranch()));

		return result;
	}
}
