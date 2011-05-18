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
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitBranchTest extends GitTest {
	@Test
	public void testListBranches() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());

		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		JSONObject clone = clone(new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute());
		String branchLocation = clone.getString(GitConstants.KEY_BRANCH);

		// list branches
		WebRequest request = new GetMethodWebRequest(branchLocation);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject branches = new JSONObject(response.getText());
		JSONArray branchesArray = branches.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, branchesArray.length());

	}
}
