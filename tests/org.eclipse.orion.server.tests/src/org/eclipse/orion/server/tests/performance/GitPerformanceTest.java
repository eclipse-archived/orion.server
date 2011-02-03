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
package org.eclipse.orion.server.tests.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.filesystem.git.GitFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.eclipse.orion.server.tests.servlets.workspace.WorkspaceServiceTest;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitPerformanceTest extends FileSystemTest {

	WebConversation webConversation;

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	@Before
	public void setUp() throws CoreException {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
	}

	WebResponse createWorkspace(String workspaceName) throws IOException, SAXException {
		WebRequest request = getCreateWorkspaceRequest(workspaceName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return response;

	}

	@Test
	public void readProjectNonDefaultLocationChildren() throws CoreException, IOException, SAXException, JSONException {

		// org.eclipse.e4.webide.server.tests.servlets.workspace.WorkspaceServiceTest.testCreateProjectNonDefaultLocation()
		// +
		//org.eclipse.e4.webide.server.tests.servlets.files.CoreFilesTest.testReadDirectoryChildren()
		// + 
		// PerformanceMeter

		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this.getClass().getName() + '#' + getMethodName() + "()"); //$NON-NLS-1$

		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#" + getMethodName();
		WebResponse response = createWorkspace(workspaceName);
		URL workspaceLocation = response.getURL();

		GitFileStore projectLocation = initRemoteGitRepository();

		try {

			for (int i = 0; i < 100; i++) {

				perfMeter.start();

				// create a project
				String projectName = "GitProject" + System.currentTimeMillis();
				JSONObject body = new JSONObject();
				body.put("ContentLocation", projectLocation.toString());
				InputStream in = new ByteArrayInputStream(body.toString().getBytes());
				WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
				if (projectName != null)
					request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
				request.setHeaderField("EclipseWeb-Version", "1");
				request.setHeaderField(ProtocolConstants.KEY_CREATE_IF_DOESNT_EXIST, Boolean.toString(Boolean.FALSE));
				setAuthentication(request);
				response = webConversation.getResponse(request);
				assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
				JSONObject project = new JSONObject(response.getText());
				assertEquals(projectName, project.getString("Name"));
				String projectId = project.optString("Id", null);
				assertNotNull(projectId);

				// get children
				request = getGetFilesRequest(projectId + "?depth=1");
				response = webConversation.getResponse(request);
				assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

				List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));

				/* expected children: .git, .gitignore, file0.txt, folder1 */
				assertEquals("Wrong number of directory children", 4, children.size());

				for (JSONObject child : children) {
					if (child.getString("Name").startsWith(".git"))
						continue; // ignore git metadata
					if (child.getBoolean("Directory")) {
						checkDirectoryMetadata(child, "folder1", null, null, null, null, null);
					} else {
						checkFileMetadata(child, "file0.txt", null, null, null, null, null, null, null);
					}
				}

				perfMeter.stop();
			}

			perfMeter.commit();
			perf.assertPerformance(perfMeter);
		} finally {
			perfMeter.dispose();
		}

		// TODO: clean up
		// FileSystemHelper.clear(root.getLocalFile());
		// FileSystemHelper.clear(repositoryPath.toFile());
	}

	@Test
	public void setFileContents() throws IOException, SAXException, CoreException, JSONException {
		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this.getClass().getName() + '#' + getMethodName() + "()"); //$NON-NLS-1$

		//create workspace
		String workspaceName = WorkspaceServiceTest.class.getName() + "#" + getMethodName();
		WebResponse response = createWorkspace(workspaceName);
		URL workspaceLocation = response.getURL();

		GitFileStore projectLocation = initRemoteGitRepository();

		// create a project
		String projectName = "GitProject" + System.currentTimeMillis();
		JSONObject body = new JSONObject();
		body.put("ContentLocation", projectLocation.toString());
		InputStream in = new ByteArrayInputStream(body.toString().getBytes());
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField("EclipseWeb-Version", "1");
		request.setHeaderField(ProtocolConstants.KEY_CREATE_IF_DOESNT_EXIST, Boolean.toString(Boolean.FALSE));
		setAuthentication(request);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject project = new JSONObject(response.getText());
		assertEquals(projectName, project.getString("Name"));
		String projectId = project.optString("Id", null);
		assertNotNull(projectId);

		// TODO: create folder

		try {

			for (int i = 0; i < 100; i++) {

				perfMeter.start();

				long now = System.currentTimeMillis();
				request = getPutFileRequest(projectId + "/file0.txt", Long.toString(now));
				response = webConversation.getResponse(request);
				assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

				perfMeter.stop();

				request = getGetFilesRequest(projectId + "/file0.txt");
				response = webConversation.getResponse(request);
				assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
				assertEquals("Invalid file content", Long.toString(now), response.getText());
			}

			perfMeter.commit();
			perf.assertPerformance(perfMeter);
		} finally {
			perfMeter.dispose();
		}

		// TODO: clean up
		// FileSystemHelper.clear(root.getLocalFile());
		// FileSystemHelper.clear(repositoryPath.toFile());
	}

	private GitFileSystem fs = new GitFileSystem();
	private IPath repositoryPath;
	private GitFileStore root;

	private GitFileStore initRemoteGitRepository() throws CoreException, IOException {
		repositoryPath = getRandomLocation();
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		URI uri = URI.create(sb.toString());
		root = (GitFileStore) fs.getStore(uri);

		root.mkdir(EFS.NONE, null);
		IFileStore file0 = root.getChild("file0.txt"); //$NON-NLS-1$
		OutputStream out = file0.openOutputStream(EFS.NONE, null);
		out.write("file0.txt content".getBytes()); //$NON-NLS-1$
		out.close();
		IFileStore folder1 = root.getChild("folder1"); //$NON-NLS-1$
		folder1.mkdir(EFS.NONE, null);
		IFileStore file1a = folder1.getChild("file1a.txt"); //$NON-NLS-1$
		out = file1a.openOutputStream(EFS.NONE, null);
		out.write("folder1/file1a.txt content".getBytes()); //$NON-NLS-1$
		out.close();
		IFileStore file1b = folder1.getChild("file1b.txt"); //$NON-NLS-1$
		out = file1b.openOutputStream(EFS.NONE, null);
		out.write("folder1/file1b.txt content".getBytes()); //$NON-NLS-1$
		out.close();
		IFileStore subfolder1 = folder1.getChild("subfolder1"); //$NON-NLS-1$
		subfolder1.mkdir(EFS.NONE, null);
		IFileStore file3 = subfolder1.getChild("file3.txt"); //$NON-NLS-1$
		out = file3.openOutputStream(EFS.NONE, null);
		out.write("folder1/subfolder1/file3.txt content".getBytes()); //$NON-NLS-1$
		out.close();

		// remove local clone
		FileSystemHelper.clear(root.getLocalFile());
		return root;
	}

	private IPath getRandomLocation() {
		return FileSystemHelper.getRandomLocation(FileSystemHelper.getTempDir());
	}

	private static String getMethodName() {
		final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		return ste[3].getMethodName();
	}
}
