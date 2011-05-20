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
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;
import java.net.URI;

import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitBranchTest extends GitTest {
	@Test
	public void testListBranches() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		JSONObject clone = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute());
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// list branches
		WebRequest request = getGetRequest(branchesLocation);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject branches = new JSONObject(response.getText());
		JSONArray branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, branchesArray.length());

		// validate branch metadata
		JSONObject branch = branchesArray.getJSONObject(0);
		assertEquals(Constants.MASTER, branch.getString(ProtocolConstants.KEY_NAME));
		assertBranchUri(branch.getString(ProtocolConstants.KEY_LOCATION));
		// that's it for now
	}

	@Test
	public void testAddRemoveBranch() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		JSONObject clone = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute());
		String branchesLocation = clone.getString(GitConstants.KEY_BRANCH);

		// create branch
		WebResponse response = branch(branchesLocation, "a");
		String branchLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);

		// check details
		WebRequest request = getGetRequest(branchLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// list branches
		request = getGetRequest(branchesLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject branches = new JSONObject(response.getText());
		JSONArray branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(2, branchesArray.length());

		// remove branch
		request = getDeleteGitBranchRequest(branchLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// list branches again, make sure it's gone
		request = getGetRequest(branchesLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		branches = new JSONObject(response.getText());
		branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, branchesArray.length());
	}

	private WebRequest getDeleteGitBranchRequest(String location) {
		String requestURI;
		if (location.startsWith("http://")) {
			requestURI = location;
		} else {
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.BRANCH_RESOURCE + location;
		}
		WebRequest request = new DeleteMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

}
