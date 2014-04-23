/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.Slug;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.tests.AbstractServerTest;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Common base class for file system tests.
 */
public abstract class FileSystemTest extends AbstractServerTest {

	public static final String FILE_SERVLET_LOCATION = "file/";
	private static String FILESTORE_PREFIX;

	/**
	 * Location of the test project within the file servlet namespace.
	 */
	protected String testProjectBaseLocation = "";

	/**
	 * The local file system location of the test project.
	 */
	protected String testProjectLocalFileLocation = "";

	protected WebConversation webConversation;

	/**
	 * Creates a test project with the given name.
	 * @throws SAXException 
	 * @throws IOException 
	 */
	protected void createTestProject(String name) throws Exception {
		//create workspace
		String workspaceName = getClass().getName() + "#" + name;
		URI workspaceLocation = createWorkspace(workspaceName);

		//create a project
		String projectName = name + "Project";
		WebRequest request = getCreateProjectRequest(workspaceLocation, projectName, null);
		WebResponse response = webConversation.getResponse(request);
		if (response.getResponseCode() != HttpURLConnection.HTTP_CREATED) {
			String msg = "Unexpected " + response.getResponseCode() + " response creating project " + projectName + ": " + response.getText();
			System.out.println(msg);
			LogHelper.log(new Status(IStatus.ERROR, ServerTestsActivator.PI_TESTS, msg));
			fail(msg);
		}
		IPath workspacePath = new Path(workspaceLocation.getPath());
		String workspaceId = new Path(workspaceLocation.getPath()).segment(workspacePath.segmentCount() - 1);
		testProjectBaseLocation = "/" + workspaceId + '/' + projectName;
		JSONObject project = new JSONObject(response.getText());
		testProjectLocalFileLocation = "/" + project.optString(ProtocolConstants.KEY_ID, null);
	}

	protected boolean checkDirectoryExists(String path) throws CoreException {
		IFileStore dir = EFS.getStore(makeLocalPathAbsolute(path));
		return (dir.fetchInfo().exists() && dir.fetchInfo().isDirectory());
	}

	protected boolean checkFileExists(String path) throws CoreException {
		IFileStore file = EFS.getStore(makeLocalPathAbsolute(path));
		return (file.fetchInfo().exists() && !file.fetchInfo().isDirectory());
	}

	protected boolean checkContentEquals(File expected, String actual) throws IOException, CoreException {
		File actualContent = EFS.getStore(makeLocalPathAbsolute(actual)).toLocalFile(EFS.NONE, null);
		return FileUtils.contentEquals(expected, actualContent);
	}

	protected static void clearWorkspace() throws CoreException {
		IMetaStore store = OrionConfiguration.getMetaStore();
		List<String> userIds = store.readAllUsers();
		for (String userId : userIds) {
			IFileStore userHome = OrionConfiguration.getMetaStore().getUserHome(userId);
			UserInfo user = store.readUser(userId);
			for (String workspaceId : user.getWorkspaceIds()) {
				WorkspaceInfo workspace = store.readWorkspace(workspaceId);
				List<String> projectNames = workspace.getProjectNames();
				for (String projectName : projectNames) {
					ProjectInfo project = store.readProject(workspaceId, projectName);
					URI contentLocation = project.getContentLocation();
					// delete the project folders
					IFileStore projectDir = EFS.getStore(contentLocation);
					if (userHome.isParentOf(projectDir)) {
						for (IFileStore child : projectDir.childStores(EFS.NONE, null)) {
							if (!child.getName().equals("project.json")) {
								child.delete(EFS.NONE, null);
							}
						}
					}
					// delete the project metadata
					store.deleteProject(workspaceId, projectName);
				}
				// delete the workspace metadata
				store.deleteWorkspace(userId, workspaceId);
			}
		}
	}

	/**
	 * Allows a subclass test to insert a different base location for the file system contents
	 * of that test. The return value should be an empty string, or a path with leading
	 * slash and no trailing slash.
	 */
	protected String getTestBaseFileSystemLocation() {
		return testProjectLocalFileLocation;
	}

	/**
	 * Allows a subclass test to insert a different base resource URI, such as the 
	 * workspace/project for that test.  The return value should be an empty string, or a path with leading
	 * slash and no trailing slash.
	 */
	protected String getTestBaseResourceURILocation() {
		return testProjectBaseLocation;
	}

	protected static File getWorkspaceRoot() throws CoreException {
		return EFS.getStore(URI.create(FILESTORE_PREFIX)).toLocalFile(EFS.NONE, null);
	}

	/**
	 * Creates a new directory in the server's local file system at the root location for the file servlet.
	 */
	protected void createDirectory(String path) throws CoreException {
		IFileInfo info = null;
		URI location = makeLocalPathAbsolute(path);
		IFileStore dir = EFS.getStore(location);
		dir.mkdir(EFS.NONE, null);
		info = dir.fetchInfo();
		assertTrue("Coudn't create directory " + path, info.exists() && info.isDirectory());
	}

	protected static void createFile(URI uri, String fileContent) throws CoreException {
		IFileStore outputFile = EFS.getStore(uri);
		outputFile.delete(EFS.NONE, null);
		InputStream input = new ByteArrayInputStream(fileContent.getBytes());
		transferData(input, outputFile.openOutputStream(EFS.NONE, null));
		IFileInfo info = outputFile.fetchInfo();
		assertTrue("Coudn't create file " + uri, info.exists() && !info.isDirectory());
	}

	protected void createFile(String path, String fileContent) throws CoreException {
		createFile(makeLocalPathAbsolute(path), fileContent);
	}

	/**
	 * Returns an absolute resource URI path within the test project for the given project
	 * relative path. Returns <code>null</code> if there is no test project configured.
	 */
	private String getAbsolutePath(String path) {
		try {
			final String baseLocation = getTestBaseResourceURILocation();
			Path basePath = new Path(baseLocation);
			if (basePath.segmentCount() < 2)
				return null;
			String workspaceId = basePath.segment(0);
			String projectName = basePath.segment(1);
			ProjectInfo projectInfo = new ProjectInfo();
			projectInfo.setFullName(projectName);
			projectInfo.setWorkspaceId(workspaceId);
			IFileStore fileStore = OrionConfiguration.getMetaStore().getDefaultContentLocation(projectInfo);
			String absolutePath = fileStore.toLocalFile(EFS.NONE, null).toString();
			return absolutePath;
		} catch (CoreException e) {
			fail(e.getLocalizedMessage());
		}
		return null;
	}

	protected URI makeLocalPathAbsolute(String path) {
		String absolutePath = getAbsolutePath(path);
		try {
			return URIUtil.fromString(absolutePath);
		} catch (URISyntaxException e) {
			fail(e.getMessage());
			return null;
		}
	}

	protected static void initializeWorkspaceLocation() {
		IFileStore root = OrionConfiguration.getRootLocation();
		FILESTORE_PREFIX = root.toURI().toString() + "/";
	}

	protected void remove(String path) throws CoreException {
		String absolutePath = getAbsolutePath(path);
		if (absolutePath != null) {
			IFileStore outputFile = EFS.getStore(URI.create(absolutePath));
			outputFile.delete(EFS.NONE, null);
		}
	}

	private static void transferData(InputStream input, OutputStream output) {
		try {
			try {
				int c = 0;
				while ((c = input.read()) != -1)
					output.write(c);
			} finally {
				input.close();
				output.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
			assertTrue(e.toString(), false);
		}
	}

	protected void checkDirectoryMetadata(JSONObject dirObject, String name, String path, Long localTimestamp, Boolean isReadonly, Boolean isExecutable, String location) throws JSONException {

		assertTrue("Expected directory but found file", dirObject.getBoolean(ProtocolConstants.KEY_DIRECTORY));

		if (name != null) {
			assertEquals("Invalid directory name", name, dirObject.getString(ProtocolConstants.KEY_NAME));
		}
		if (path != null) {
			assertEquals("Invalid directory path", path, dirObject.getString(ProtocolConstants.KEY_PATH));
		}
		if (localTimestamp != null) {
			if (localTimestamp < 0) {
				assertNotNull("Directory timestamp should not be null", dirObject.getLong(ProtocolConstants.KEY_LOCAL_TIMESTAMP));
			} else {
				assertEquals("Invalid directory timestamp", localTimestamp, new Long(dirObject.getLong(ProtocolConstants.KEY_LOCAL_TIMESTAMP)));
			}
		}
		if (location != null) {
			assertEquals("Invalid directory location", location, dirObject.getString(ProtocolConstants.KEY_LOCATION));
		}
		if (isReadonly != null && isExecutable != null) {
			JSONObject attributes = dirObject.getJSONObject(ProtocolConstants.KEY_ATTRIBUTES);
			assertNotNull("Expected Attributes section in directory metadata", attributes);
			if (isReadonly != null) {
				assertEquals("Ibvalid directory readonly attribute", isReadonly, attributes.getBoolean(ProtocolConstants.KEY_ATTRIBUTE_READ_ONLY));
			}
			if (isExecutable != null) {
				assertEquals("Invalid directory executable attribute", isExecutable, attributes.getBoolean(ProtocolConstants.KEY_ATTRIBUTE_EXECUTABLE));
			}
		}
	}

	protected void checkFileMetadata(JSONObject fileObject, String name, Long localTimestamp, String charset, String contentType, String location, Long length, Boolean isReadonly, Boolean isExecutable, String parentName) throws JSONException {

		assertFalse("Expected file but found directory", fileObject.getBoolean(ProtocolConstants.KEY_DIRECTORY));

		if (name != null) {
			assertEquals("Invalid file name", name, fileObject.getString(ProtocolConstants.KEY_NAME));
		}
		if (localTimestamp != null) {
			if (localTimestamp < 0) {
				assertNotNull("File timestamp should not be null", fileObject.getLong(ProtocolConstants.KEY_LOCAL_TIMESTAMP));
			} else {
				assertEquals("Invalid file timestamp", localTimestamp, new Long(fileObject.getLong(ProtocolConstants.KEY_LOCAL_TIMESTAMP)));
			}
		}
		if (charset != null) {
			assertEquals("Invalid file charset", charset, fileObject.getString("Charset"));
		}
		if (contentType != null) {
			assertEquals("Invalid file content type", contentType, fileObject.getString("ContentType"));
		}
		if (location != null) {
			assertEquals("Invalid file location", location, fileObject.getString(ProtocolConstants.KEY_LOCATION));
		}
		if (length != null) {
			assertEquals("Invalid file length", length, new Long(fileObject.getLong(ProtocolConstants.KEY_LENGTH)));
		}
		if (isReadonly != null || isExecutable != null) {
			int attrs = EFS.getLocalFileSystem().attributes();
			JSONObject attributes = fileObject.optJSONObject(ProtocolConstants.KEY_ATTRIBUTES);
			assertNotNull("Expected Attributes section in file metadata", attributes);
			if (isReadonly != null && ((attrs & EFS.ATTRIBUTE_READ_ONLY) != 0)) {
				assertEquals("Invalid file readonly attribute", isReadonly.booleanValue(), attributes.getBoolean(ProtocolConstants.KEY_ATTRIBUTE_READ_ONLY));
			}
			if (isExecutable != null && ((attrs & EFS.ATTRIBUTE_EXECUTABLE) != 0)) {
				assertEquals("Invalid file executable attribute", isExecutable.booleanValue(), attributes.getBoolean(ProtocolConstants.KEY_ATTRIBUTE_EXECUTABLE));
			}
		}
		if (parentName != null) {
			JSONArray parents = fileObject.getJSONArray(ProtocolConstants.KEY_PARENTS);
			assertNotNull("Expected Parents array in file metadata", parents);
			assertEquals("Expected single parent item", 1, parents.length());
			JSONObject parent = parents.getJSONObject(0);
			assertNotNull("Invalid parent item", parent);
			assertEquals("Invalid file parent name", parentName, parent.get(ProtocolConstants.KEY_NAME));
		}
	}

	protected WebRequest getDeleteFilesRequest(String uri) {
		WebRequest request = new DeleteMethodWebRequest(makeResourceURIAbsolute(uri));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	protected List<JSONObject> getDirectoryChildren(JSONObject dirObject) throws JSONException {
		assertTrue("Expected directory but found file", dirObject.getBoolean(ProtocolConstants.KEY_DIRECTORY));
		List<JSONObject> children = new ArrayList<JSONObject>();
		try {
			JSONArray chidrenArray = dirObject.getJSONArray(ProtocolConstants.KEY_CHILDREN);
			for (int i = 0; i < chidrenArray.length(); i++) {
				children.add(chidrenArray.getJSONObject(i));
			}
		} catch (JSONException e) {
			// when no Children section attached return empty list
		}
		return children;
	}

	/**
	 * Creates a request to get the resource at the given location.
	 * @param location Either an absolute URI, or a workspace-relative URI
	 */
	protected WebRequest getGetFilesRequest(String location) {
		String requestURI = makeResourceURIAbsolute(location);
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	protected JSONObject getNewDirJSON(String dirName) throws JSONException {
		JSONObject json = new JSONObject();
		json.put(ProtocolConstants.KEY_NAME, dirName);
		json.put(ProtocolConstants.KEY_DIRECTORY, true);
		return json;
	}

	protected JSONObject getNewFileJSON(String dirName) throws JSONException {
		JSONObject json = new JSONObject();
		if (dirName != null)
			json.put(ProtocolConstants.KEY_NAME, dirName);
		json.put(ProtocolConstants.KEY_DIRECTORY, false);
		json.put("Charset", "UTF-8");
		json.put("ContentType", "text/plain");
		return json;
	}

	protected WebRequest getPostFilesRequest(String uri, String json, String slug) {
		try {
			WebRequest request = new PostMethodWebRequest(makeResourceURIAbsolute(uri), IOUtilities.toInputStream(json), "application/json");
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, Slug.encode(slug));
			request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
			setAuthentication(request);
			return request;
		} catch (UnsupportedEncodingException e) {
			fail(e.getMessage());
		}
		//can never get here
		return null;
	}

	protected WebRequest getPutFileRequest(String uri, String body) {
		try {
			WebRequest request = new PutMethodWebRequest(makeResourceURIAbsolute(uri), IOUtilities.toInputStream(body), "application/json");
			request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
			setAuthentication(request);
			return request;
		} catch (UnsupportedEncodingException e) {
			fail(e.getMessage());
		}
		//can never get here
		return null;
	}

	protected WebRequest getPutFileRequest(String uri, byte[] body) {
		ByteArrayInputStream source = new ByteArrayInputStream(body);
		WebRequest request = new PutMethodWebRequest(makeResourceURIAbsolute(uri), source, "application/json");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	/**
	 * Makes a URI absolute. If the provided URI is relative, it is assumed to be relative to the workspace location (file servlet location).
	 * If the provided URI is already absolute it is returned as-is.
	 * The provided URI must be correctly encoded.
	 */
	protected String makeResourceURIAbsolute(String uriString) {
		try {
			URI uri = new URI(uriString);
			if (uri.isAbsolute())
				return uriString;
			if (uriString.startsWith("/")) {
				return toAbsoluteURI(uriString);
			}
		} catch (URISyntaxException e) {
			//unencoded string - fall through
		}
		try {
			if (uriString.startsWith(FILE_SERVLET_LOCATION))
				return URIUtil.fromString(SERVER_LOCATION + uriString).toString();
			//avoid double slash
			if (uriString.startsWith("/"))
				uriString = uriString.substring(1);
			String path = new Path(FILE_SERVLET_LOCATION).append(getTestBaseResourceURILocation()).append(uriString).toString();
			return new URI(SERVER_LOCATION + path).toString();
		} catch (URISyntaxException e) {
			fail(e.getMessage());
			return null;
		}
	}

	protected WebRequest getCreateWorkspaceRequest(String workspaceName) {
		WebRequest request = new PostMethodWebRequest(SERVER_LOCATION + "/workspace");
		if (workspaceName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, workspaceName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	protected WebRequest getCreateProjectRequest(URI workspaceLocation, String projectName, String projectLocation) throws JSONException, IOException {
		workspaceLocation = addSchemeHostPort(workspaceLocation);
		JSONObject body = new JSONObject();
		if (projectLocation != null)
			body.put(ProtocolConstants.KEY_CONTENT_LOCATION, projectLocation);
		InputStream in = IOUtilities.toInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(workspaceLocation.toString(), in, "UTF-8");
		if (projectName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, projectName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	protected URI addSchemeHostPort(URI uri) {
		String scheme = uri.getScheme();
		String host = uri.getHost();
		int port = uri.getPort();
		if (scheme == null) {
			scheme = "http";
		}
		if (host == null) {
			host = "localhost";
		}
		if (port == -1) {
			port = 8080;
		}
		try {
			return new URI(scheme, uri.getUserInfo(), host, port, uri.getPath(), uri.getQuery(), uri.getFragment());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a new workspace, and returns the raw response object.
	 */
	protected WebResponse basicCreateWorkspace(String workspaceName) throws IOException, SAXException {
		WebRequest request = getCreateWorkspaceRequest(workspaceName);
		WebResponse response = webConversation.getResponse(request);
		if (response.getResponseCode() != HttpURLConnection.HTTP_OK) {
			System.err.println(response.getResponseMessage());
			assertEquals("Unexpected failure creating workspace with name: " + workspaceName, HttpURLConnection.HTTP_OK, response.getResponseCode());
		}
		return response;

	}

	/**
	 * Creates a new workspace, and returns the URI of the resulting resource.
	 */
	protected URI createWorkspace(String workspaceName) throws IOException, SAXException {
		WebResponse response = basicCreateWorkspace(workspaceName);
		return SERVER_URI.resolve(response.getHeaderField(ProtocolConstants.HEADER_LOCATION));
	}

}
