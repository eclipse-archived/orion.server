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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitStatusTest extends GitTest {
	@Test
	@Ignore("not yet implemented")
	public void testStatus() throws IOException, SAXException, URISyntaxException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		String gitStatusUri = project.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(gitStatusUri);

		WebRequest request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		//		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getResponseCode());
		StringBuffer sb = new StringBuffer();
		sb.append("# On branch master").append("\n");
		sb.append("nothing to commit (working directory clean)").append("\n");
		assertEquals(sb.toString(), response.getText());
	}

	@Test
	@Ignore("not yet implemented")
	public void testStatusModifiedByOrion() throws IOException, SAXException, URISyntaxException, JSONException {
		URI workspaceLocation = createWorkspace(getMethodName());

		String projectName = getMethodName();
		WebResponse response = createProjectWithContentLocation(workspaceLocation, projectName, gitDir.toString());

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString(ProtocolConstants.KEY_NAME));
		String projectId = project.optString(ProtocolConstants.KEY_ID, null);
		assertNotNull(projectId);

		WebRequest request = getPutFileRequest(projectId + "/test.txt", "hello");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		String gitStatusUri = project.optString(GitConstants.KEY_STATUS, null);
		assertNotNull(gitStatusUri);

		request = getGetGitStatusRequest(gitStatusUri);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		StringBuffer sb = new StringBuffer();
		sb.append("# On branch master").append("\n");
		sb.append("# Changed but not updated:").append("\n");
		sb.append("#   (use \"git add <file>...\" to update what will be committed)").append("\n");
		sb.append("#   (use \"git checkout -- <file>...\" to discard changes in working directory)").append("\n");
		sb.append("#").append("\n");
		sb.append("#       modified:   test.txt").append("\n");
		sb.append("#").append("\n");
		sb.append("no changes added to commit (use \"git add\" and/or \"git commit -a\")").append("\n");
		assertEquals(sb.toString(), response.getText());
	}
}
