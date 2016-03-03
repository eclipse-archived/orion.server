/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Tests to ensure that the project list in a workspace in a SimpleMetaStore can be successfully deleted concurrently
 * from separate threads. See Bug 474557
 */
public class SimpleMetaStoreWorkspaceProjectListConcurrencyTests extends FileSystemTest {

	private int USERS_NUMBER = 4;

	private class WorkspaceObject {
		private int PROJECTS_NUMBER = 40;
		private URI workspaceLocation;

		public void setWorkspaceLocation(URI workspaceLocation) {
			this.workspaceLocation = workspaceLocation;
		}

		private List<WebConversation> webConversationList = new ArrayList<WebConversation>();
		private String password;

		public String getPassword() {
			return password;
		}

		public String getLogin() {
			return login;
		}

		private String login;

		public WorkspaceObject(String login, String password) {
			this.login = login;
			this.password = password;
			for (int i = 0; i < PROJECTS_NUMBER; i++) {
				WebConversation webConversation = new WebConversation();
				webConversation.setExceptionsThrownOnErrorStatus(false);
				webConversationList.add(webConversation);
			}
		}

		public List<WebConversation> getWebConversationList() {
			return webConversationList;
		}

		public String getWorkspaceName() {
			return workspaceLocation.getPath().split("/")[2];
		}

		public URI getWorkspaceLocation() {
			return workspaceLocation;
		}

	}

	private List<WorkspaceObject> workspaceObjectList = new ArrayList<WorkspaceObject>();

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	@Before
	public void setUp() throws CoreException, IOException, SAXException {
		String userLogin = testUserLogin;
		for (int i = 0; i < USERS_NUMBER; i++) {
			WorkspaceObject workspaceObject = new WorkspaceObject(userLogin + "_" + i, testUserPassword);
			webConversation = workspaceObject.getWebConversationList().get(0);
			testUserLogin = workspaceObject.getLogin();
			testUserPassword = workspaceObject.getPassword();
			setUpAuthorization();
			createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
			workspaceObject.setWorkspaceLocation(workspaceLocation);
			workspaceObjectList.add(workspaceObject);
		}
	}

	private Thread createThreadForDeletion(final String name, final WebConversation webConversation, final WebRequest request) {
		Runnable runnable = new Runnable() {
			public void run() {
				try {
					WebResponse response = webConversation.getResponse(request);
					assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
				} catch (IOException e) {
					fail(e.getLocalizedMessage());
				} catch (SAXException e) {
					fail(e.getLocalizedMessage());
				}
			}
		};

		Thread thread = new Thread(runnable, "SimpleMetaStoreWorkspaceProjectListConcurrencyTests-" + name);
		return thread;
	}

	@Test
	public void testSimpleMetaStoreDeleteProjectConcurrency() throws IOException, SAXException, JSONException, CoreException {

		List<Thread> threads = new ArrayList<Thread>();

		for (int j = 0; j < workspaceObjectList.size(); j++) {
			List<WebConversation> webConversationList = workspaceObjectList.get(j).getWebConversationList();

			ArrayList<WebRequest> webRequestList = new ArrayList<WebRequest>();
			URI workspaceLocation1 = workspaceObjectList.get(j).getWorkspaceLocation();
			testUserLogin = workspaceObjectList.get(j).getLogin();
			testUserPassword = workspaceObjectList.get(j).getPassword();

			for (int i = 0; i < webConversationList.size(); i++) {
				// create a project
				String projectName = "TestProject" + i;
				WebRequest request = getCreateProjectRequest(workspaceLocation1, projectName, null);
				WebResponse response = webConversationList.get(i).getResponse(request);
				assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
				String projectLocation = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
				// update the global variables for the test
				IPath workspacePath = new Path(workspaceLocation1.getPath());
				String workspaceId = new Path(workspaceLocation1.getPath()).segment(workspacePath.segmentCount() - 1);
				testProjectBaseLocation = "/" + workspaceId + '/' + projectName;
				JSONObject project = new JSONObject(response.getText());
				testProjectLocalFileLocation = "/" + project.optString(ProtocolConstants.KEY_ID, null);
				String contentLocation = project.optString(ProtocolConstants.KEY_CONTENT_LOCATION);
				// IFileStore projectStore = EFS.getStore(makeLocalPathAbsolute(""));

				// add a file in the project
				String fileName = "file.txt";
				request = getPostFilesRequest(contentLocation, getNewFileJSON(fileName).toString(), fileName);
				response = webConversationList.get(i).getResponse(request);
				assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
				assertEquals("Response should contain file metadata in JSON, but was " + response.getText(), "application/json", response.getContentType());
				JSONObject responseObject = new JSONObject(response.getText());
				assertNotNull("No file information in response", responseObject);
				checkFileMetadata(responseObject, fileName, null, null, null, null, null, null, null, projectName);

				WebRequest request1 = new DeleteMethodWebRequest(toAbsoluteURI(projectLocation));
				request1.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
				setAuthentication(request1);
				webRequestList.add(request1);
			}

			WorkspaceInfo workspaceLocal = OrionConfiguration.getMetaStore().readWorkspace(workspaceObjectList.get(j).getWorkspaceName());
			List<String> projectListFinal = workspaceLocal.getProjectNames();
			// Assertion for check a number of projects before deletion.
			assertEquals(webConversationList.size(), projectListFinal.size());

			for (int i = 0; i < webConversationList.size(); i++) {
				threads.add(createThreadForDeletion(workspaceObjectList.get(j).getWorkspaceName() + " TestProject" + i, webConversationList.get(i),
						webRequestList.get(i)));
			}
		}

		for (int i = 0; i < threads.size(); i++) {
			threads.get(i).start();
		}

		for (int i = 0; i < threads.size(); i++) {
			try {
				threads.get(i).join();
			} catch (InterruptedException e) {
				// just continue
			}
		}

		for (int i = 0; i < workspaceObjectList.size(); i++) {

			WebRequest request = new GetMethodWebRequest(addSchemeHostPort(workspaceObjectList.get(i).getWorkspaceLocation()).toString());
			setAuthentication(request);

			WebResponse response = webConversation.getResponse(request);
			JSONObject workspace = new JSONObject(response.getText());
			assertNotNull(workspace);
			JSONArray projects = workspace.getJSONArray(ProtocolConstants.KEY_PROJECTS);
			assertEquals(0, projects.length());
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

			WorkspaceInfo workspaceLocal = OrionConfiguration.getMetaStore().readWorkspace(workspaceObjectList.get(i).getWorkspaceName());
			List<String> projectListFinal = workspaceLocal.getProjectNames();
			assertEquals(0, projectListFinal.size());
		}
	}
}