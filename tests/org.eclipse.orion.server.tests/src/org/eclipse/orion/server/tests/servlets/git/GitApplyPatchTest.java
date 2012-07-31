/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
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
import static org.junit.Assert.assertNotNull;

import com.meterware.httpunit.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Diff;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

public class GitApplyPatchTest extends GitTest {

	private static final String EOL = "\r\n"; //$NON-NLS-1$

	@Test
	public void testApplyPatch_addFile() throws Exception {
		// clone: create
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		StringBuilder sb = new StringBuilder();
		sb.append("diff --git a/new.txt b/new.txt").append("\n");
		sb.append("new file mode 100644").append("\n");
		sb.append("index 0000000..8013df8 100644").append("\n");
		sb.append("--- /dev/null").append("\n");
		sb.append("+++ b/new.txt").append("\n");
		sb.append("@@ -0,0 +1 @@").append("\n");
		sb.append("+newborn").append("\n");
		sb.append("\\ No newline at end of file").append("\n");

		/*JSONObject patchResult = */patch(gitDiffUri, sb.toString());
		//		assertEquals("Ok", patchResult.getString(GitConstants.KEY_RESULT));

		JSONObject newTxt = getChild(project, "new.txt");
		assertEquals("newborn", getFileContent(newTxt));
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		assertStatus(new StatusResult().setUntrackedNames("new.txt"), gitStatusUri);
	}

	@Test
	public void testApplyPatch_deleteFile() throws Exception {
		// clone: create
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		StringBuilder sb = new StringBuilder();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("deleted file mode 100644").append("\n");
		sb.append("index 8013df8..0000000 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ /dev/null").append("\n");
		sb.append("@@ -1 +0,0 @@").append("\n");
		sb.append("-test").append("\n");

		/*JSONObject patchResult =*/patch(gitDiffUri, sb.toString());
		//		assertEquals("Ok", patchResult.getString(GitConstants.KEY_RESULT));

		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		assertStatus(new StatusResult().setMissingNames("test.txt"), gitStatusUri);
	}

	@Test
	public void testApplyPatch_modifyFile() throws Exception {
		// clone: create
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		StringBuilder sb = new StringBuilder();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..8013df8 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-test").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+patched").append("\n");
		sb.append("\\ No newline at end of file").append("\n");

		/*JSONObject patchResult = */patch(gitDiffUri, sb.toString());
		//		assertEquals("Ok", patchResult.getString(GitConstants.KEY_RESULT));

		JSONObject testTxt = getChild(project, "test.txt");
		assertEquals("patched", getFileContent(testTxt));
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		assertStatus(new StatusResult().setModifiedNames("test.txt").setModifiedContents("patched"), gitStatusUri);
	}

	// TODO
	@Ignore("not reported as a format error")
	@Test
	public void testApplyPatch_modifyFileFormatError() throws Exception {
		// clone: create
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		StringBuilder sb = new StringBuilder();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("malformed patch").append("\n");

		/*JSONObject patchResult =*/patch(gitDiffUri, sb.toString());
		//		assertNull(patchResult.optString(GitConstants.KEY_RESULT, null));
		//		assertNotNull(patchResult.getJSONArray("FormatErrors"));

		// nothing has changed
		JSONObject testTxt = getChild(project, "test.txt");
		assertEquals("test", getFileContent(testTxt));
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		assertStatus(StatusResult.CLEAN, gitStatusUri);
	}

	@Test
	public void testApplyPatch_modifyFileApplyError() throws Exception {
		// clone: create
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);

		// get project metadata
		WebRequest request = getGetFilesRequest(project.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		project = new JSONObject(response.getText());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);

		gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitDiffUri = gitSection.getString(GitConstants.KEY_DIFF);

		StringBuilder sb = new StringBuilder();
		sb.append("diff --git a/test.txt b/test.txt").append("\n");
		sb.append("index 30d74d2..8013df8 100644").append("\n");
		sb.append("--- a/test.txt").append("\n");
		sb.append("+++ b/test.txt").append("\n");
		sb.append("@@ -1 +1 @@").append("\n");
		sb.append("-xxx").append("\n");
		sb.append("\\ No newline at end of file").append("\n");
		sb.append("+patched").append("\n");
		sb.append("\\ No newline at end of file").append("\n");

		/*JSONObject patchResult =*/patch(gitDiffUri, sb.toString());
		//		assertNull(patchResult.optString(GitConstants.KEY_RESULT, null));
		//		assertNotNull(patchResult.getJSONArray("ApplyErrors"));

		// nothing has changed
		JSONObject testTxt = getChild(project, "test.txt");
		assertEquals("test", getFileContent(testTxt));
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		assertStatus(StatusResult.CLEAN, gitStatusUri);
	}

	private void patch(final String gitDiffUri, String patch) throws IOException, SAXException {
		WebRequest request = getPostGitDiffRequest(gitDiffUri, patch);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		// TODO: see bug 366008
		// return new JSONObject(response.getText());
	}

	private static WebRequest getPostGitDiffRequest(String location, String patch) throws UnsupportedEncodingException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else if (location.startsWith("/"))
			requestURI = SERVER_LOCATION + location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + Diff.RESOURCE + location;

		String boundary = new UniversalUniqueIdentifier().toBase64String();
		StringBuilder sb = new StringBuilder();
		sb.append("--" + boundary + EOL);
		sb.append("Content-Disposition: form-data; name=\"radio\"" + EOL);
		sb.append(EOL);
		sb.append("fileRadio" + EOL);
		sb.append("--" + boundary + EOL);
		sb.append("Content-Disposition: form-data; name=\"uploadedfile\"; filename=\"\"" + EOL);
		sb.append(ProtocolConstants.HEADER_CONTENT_TYPE + ": plain/text" + EOL + EOL); //$NON-NLS-1$
		sb.append(patch);
		sb.append(EOL);
		// see GitDiffHandlerV1.readPatch(ServletInputStream, String)
		sb.append(EOL + "--" + boundary + "--" + EOL);
		patch = sb.toString();

		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(patch), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		request.setHeaderField(ProtocolConstants.HEADER_CONTENT_TYPE, "multipart/related; boundary=" + boundary); //$NON-NLS-1$
		setAuthentication(request);
		return request;
	}

}
