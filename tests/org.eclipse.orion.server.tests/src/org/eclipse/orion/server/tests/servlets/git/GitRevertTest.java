/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitRevertTest extends GitTest {

	@Test
	public void testRevert() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "first line\nsec. line\nthird line\n");

			addFile(testTxt);

			commitFile(testTxt, "lines in test.txt", false);

			JSONArray commitsArray = log(gitHeadUri);
			assertEquals(2, commitsArray.length());
			JSONObject commit = commitsArray.getJSONObject(0);
			assertEquals("lines in test.txt", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

			String toRevert = commit.getString(ProtocolConstants.KEY_NAME);

			// REVERT
			JSONObject revert = revert(gitHeadUri, toRevert);
			assertEquals("OK", revert.getString(GitConstants.KEY_RESULT));

			// new revert commit is present
			commitsArray = log(gitHeadUri);
			assertEquals(3, commitsArray.length());
			commit = commitsArray.getJSONObject(0);

			String revertMessage = commit.optString(GitConstants.KEY_COMMIT_MESSAGE);
			assertEquals(true, revertMessage != null);
			if (revertMessage != null) {
				assertEquals(true, revertMessage.startsWith("Revert \"lines in test.txt\""));
			}
		}
	}

	@Test
	public void testRevertFailure() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		IPath[] clonePaths = createTestProjects(workspaceLocation);

		for (IPath clonePath : clonePaths) {
			// clone a  repo
			JSONObject clone = clone(clonePath);
			String cloneContentLocation = clone.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

			// get project/folder metadata
			WebRequest request = getGetRequest(cloneContentLocation);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
			JSONObject folder = new JSONObject(response.getText());

			JSONObject gitSection = folder.getJSONObject(GitConstants.KEY_GIT);
			String gitHeadUri = gitSection.getString(GitConstants.KEY_HEAD);

			JSONObject testTxt = getChild(folder, "test.txt");
			modifyFile(testTxt, "first line\nsec. line\nthird line\n");

			addFile(testTxt);

			commitFile(testTxt, "lines in test.txt", false);

			JSONArray commitsArray = log(gitHeadUri);
			assertEquals(2, commitsArray.length());
			JSONObject commit = commitsArray.getJSONObject(0);
			assertEquals("lines in test.txt", commit.get(GitConstants.KEY_COMMIT_MESSAGE));

			String toRevert = commit.getString(ProtocolConstants.KEY_NAME);
			modifyFile(testTxt, "first line\nsec. line\nthird line\nfourth line\n");
			addFile(testTxt);

			// REVERT
			JSONObject revert = revert(gitHeadUri, toRevert);
			assertEquals("FAILURE", revert.getString(GitConstants.KEY_RESULT));

			// there's no new revert commit
			commitsArray = log(gitHeadUri);
			assertEquals(2, commitsArray.length());
			commit = commitsArray.getJSONObject(0);

			String revertMessage = commit.optString(GitConstants.KEY_COMMIT_MESSAGE);
			assertEquals(true, revertMessage != null);
			if (revertMessage != null) {
				assertEquals(false, revertMessage.startsWith("Revert \"lines in test.txt\""));
			}
		}
	}

	private JSONObject revert(String gitHeadUri, String toRevert) throws JSONException, IOException, SAXException {
		assertCommitUri(gitHeadUri);
		WebRequest request = getPostGitRevertRequest(gitHeadUri, toRevert);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	private static WebRequest getPostGitRevertRequest(String location, String toRevert) throws JSONException, UnsupportedEncodingException {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_REVERT, toRevert);
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
