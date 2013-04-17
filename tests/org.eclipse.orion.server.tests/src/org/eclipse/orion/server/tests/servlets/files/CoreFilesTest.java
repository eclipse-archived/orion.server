/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others 
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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.core.ServerConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.service.prefs.BackingStoreException;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Basic tests for {@link NewFileServlet}.
 */
public class CoreFilesTest extends FileSystemTest {

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	private void addSourceLocation(JSONObject requestObject, String sourcePath) throws JSONException {
		requestObject.put("Location", makeResourceURIAbsolute(sourcePath));
	}

	@Before
	public void setUp() throws Exception {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
		createTestProject("CoreFilesTest");
	}

	@After
	public void tearDown() throws Exception {
		clearWorkspace();
		remove("sample");
	}

	@Test
	public void testCopyFileInvalidSource() throws Exception {
		String directoryPath = "/testCopyFile/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		JSONObject requestObject = new JSONObject();
		requestObject.put("Location", "/file/this/does/not/exist/at/all");
		WebRequest request = getPostFilesRequest(directoryPath, requestObject.toString(), "destination.txt");
		request.setHeaderField("X-Create-Options", "copy");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
		assertEquals("Error", responseObject.get("Severity"));
	}

	@Test
	public void testCopyFileNoOverwrite() throws Exception {
		String directoryPath = "/testCopyFile/directory/path" + System.currentTimeMillis();
		String sourcePath = directoryPath + "/source.txt";
		String destName = "destination.txt";
		String destPath = directoryPath + "/" + destName;
		createDirectory(directoryPath);
		createFile(sourcePath, "This is the contents");
		JSONObject requestObject = new JSONObject();
		addSourceLocation(requestObject, sourcePath);
		WebRequest request = getPostFilesRequest(directoryPath, requestObject.toString(), "destination.txt");
		request.setHeaderField("X-Create-Options", "copy");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
		checkFileMetadata(responseObject, destName, null, null, null, null, null, null, null, null);
		assertTrue(checkFileExists(sourcePath));
		assertTrue(checkFileExists(destPath));
	}

	@Test
	public void testCopyFileOverwrite() throws Exception {
		String directoryPath = "/testCopyFile/directory/path" + System.currentTimeMillis();
		String sourcePath = directoryPath + "/source.txt";
		String destName = "destination.txt";
		String destPath = directoryPath + "/" + destName;
		createDirectory(directoryPath);
		createFile(sourcePath, "This is the contents");
		createFile(destPath, "Original file");

		//with no-overwrite, copy should fail
		JSONObject requestObject = new JSONObject();
		addSourceLocation(requestObject, sourcePath);
		WebRequest request = getPostFilesRequest(directoryPath, requestObject.toString(), "destination.txt");
		request.setHeaderField("X-Create-Options", "copy,no-overwrite");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, response.getResponseCode());

		//now omit no-overwrite and copy should succeed and return 200 instead of 201
		request = getPostFilesRequest(directoryPath, requestObject.toString(), "destination.txt");
		request.setHeaderField("X-Create-Options", "copy");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
		checkFileMetadata(responseObject, destName, null, null, null, null, null, null, null, null);
		assertTrue(checkFileExists(sourcePath));
		assertTrue(checkFileExists(destPath));
	}

	@Test
	public void testCreateDirectory() throws CoreException, IOException, SAXException, JSONException {
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		String dirName = "testdir";
		webConversation.setExceptionsThrownOnErrorStatus(false);

		WebRequest request = getPostFilesRequest(directoryPath, getNewDirJSON(dirName).toString(), dirName);
		WebResponse response = webConversation.getResponse(request);

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		assertTrue("Create directory response was OK, but the directory does not exist", checkDirectoryExists(directoryPath + "/" + dirName));
		assertEquals("Response should contain directory metadata in JSON, but was " + response.getText(), "application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No directory information in response", responseObject);
		checkDirectoryMetadata(responseObject, dirName, null, null, null, null, null);

		//should be able to perform GET on location header to obtain metadata
		String location = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
		request = getGetRequest(location);
		response = webConversation.getResource(request);
		assertNotNull(location);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertNotNull("No direcory information in response", responseObject);
		checkDirectoryMetadata(responseObject, dirName, null, null, null, null, null);

	}

	@Test
	public void testCreateEmptyFile() throws CoreException, IOException, SAXException, JSONException {
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		String fileName = "testfile.txt";

		WebRequest request = getPostFilesRequest(directoryPath, getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		assertTrue("Create file response was OK, but the file does not exist", checkFileExists(directoryPath + "/" + fileName));
		assertEquals("Response should contain file metadata in JSON, but was " + response.getText(), "application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No file information in response", responseObject);
		checkFileMetadata(responseObject, fileName, null, null, null, null, null, null, null, null);

		//should be able to perform GET on location header to obtain metadata
		String location = response.getHeaderField("Location");
		request = getGetRequest(location + "?parts=meta");
		response = webConversation.getResource(request);
		assertNotNull(location);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertNotNull("No direcory information in response", responseObject);
		checkFileMetadata(responseObject, fileName, null, null, null, null, null, null, null, null);
	}

	@Test
	public void testCreateFileOverwrite() throws CoreException, IOException, SAXException, JSONException {
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		String fileName = "testfile.txt";

		WebRequest request = getPostFilesRequest(directoryPath, getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//creating again at the same location should succeed but return OK rather than CREATED
		request = getPostFilesRequest(directoryPath, getNewFileJSON(fileName).toString(), fileName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		//creating with no-overwrite should fail if it already exists
		request = getPostFilesRequest(directoryPath, getNewFileJSON(fileName).toString(), fileName);
		request.setHeaderField("X-Create-Options", "no-overwrite");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, response.getResponseCode());
	}

	@Test
	public void testCreateTopLevelFile() throws CoreException, IOException, SAXException, JSONException {
		String directoryPath = "sample" + System.currentTimeMillis();
		createDirectory(directoryPath);
		String fileName = "testfile.txt";

		WebRequest request = getPostFilesRequest(directoryPath, getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);

		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		assertTrue("Create file response was OK, but the file does not exist", checkFileExists(directoryPath + "/" + fileName));
		assertEquals("Response should contain file metadata in JSON, but was " + response.getText(), "application/json", response.getContentType());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No file information in response", responseObject);
		checkFileMetadata(responseObject, fileName, null, null, null, null, null, null, null, null);

		//should be able to perform GET on location header to obtain metadata
		String location = response.getHeaderField("Location");
		request = getGetRequest(location + "?parts=meta");
		response = webConversation.getResource(request);
		assertNotNull(location);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertNotNull("No direcory information in response", responseObject);
		checkFileMetadata(responseObject, fileName, null, null, null, null, null, null, null, null);
	}

	@Test
	public void testDeleteEmptyDir() throws CoreException, IOException, SAXException {
		String dirPath = "sample/directory/path/sample" + System.currentTimeMillis();
		createDirectory(dirPath);

		WebRequest request = getDeleteFilesRequest(dirPath);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertFalse("Delete request returned OK, but the directory still exists", checkDirectoryExists(dirPath));

	}

	@Test
	public void testDeleteFile() throws CoreException, IOException, SAXException {
		String dirPath = "sample/directory/path";
		String fileName = System.currentTimeMillis() + ".txt";
		String filePath = dirPath + "/" + fileName;

		createDirectory(dirPath);
		createFile(filePath, "Sample file content");

		WebRequest request = getDeleteFilesRequest(filePath);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertFalse("Delete request returned OK, but the file still exists", checkFileExists(filePath));

		assertTrue("File was deleted but the above directory was deleted as well", checkDirectoryExists(dirPath));

	}

	@Test
	public void testDeleteNonEmptyDirectory() throws CoreException, IOException, SAXException {
		String dirPath1 = "sample/directory/path/sample1" + System.currentTimeMillis();
		String dirPath2 = "sample/directory/path/sample2" + System.currentTimeMillis();
		String fileName = "subfile.txt";
		String subDirectory = "subdirectory";

		createDirectory(dirPath1);
		createFile(dirPath1 + "/" + fileName, "Sample file content");
		createDirectory(dirPath2 + "/" + subDirectory);

		WebRequest request = getDeleteFilesRequest(dirPath1);
		WebResponse response = webConversation.getResponse(request);
		assertEquals("Could not delete directory with file", HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertFalse("Delete directory with file request returned OK, but the file still exists", checkDirectoryExists(dirPath1));

		request = getDeleteFilesRequest(dirPath2);
		response = webConversation.getResponse(request);
		assertEquals("Could not delete directory with subdirectory", HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertFalse("Delete directory with subdirectory request returned OK, but the file still exists", checkDirectoryExists(dirPath2));

	}

	@Test
	public void testDirectoryDepth() throws CoreException, IOException, SAXException, JSONException {
		String basePath = "sampe/directory/long" + System.currentTimeMillis();
		String longPath = basePath + "/dir1/dir2/dir3/dir4";
		createDirectory(longPath);

		WebRequest request = getGetFilesRequest(basePath);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertEquals("Directory information with depth = 0 return children", 0, getDirectoryChildren(new JSONObject(response.getText())).size());

		request = getGetFilesRequest(basePath + "?depth=1");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		List<JSONObject> depthChildren = getDirectoryChildren(new JSONObject(response.getText()));

		assertEquals("Directory information with depth = 1 returned too shallow", 1, depthChildren.size());
		checkDirectoryMetadata(depthChildren.get(0), "dir1", null, null, null, null, null);

		assertEquals("Directory information with depth = 1 returned too deep", 0, getDirectoryChildren(depthChildren.get(0)).size());

		request = getGetFilesRequest(basePath + "?depth=2");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		depthChildren = getDirectoryChildren(getDirectoryChildren(new JSONObject(response.getText())).get(0));

		assertEquals("Directory information with depth = 2 returned too shallow", 1, depthChildren.size());

		checkDirectoryMetadata(depthChildren.get(0), "dir2", null, null, null, null, null);

		assertEquals("Directory information with depth = 2 returned too deep", 0, getDirectoryChildren(depthChildren.get(0)).size());

		request = getGetFilesRequest(basePath + "?depth=3");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		depthChildren = getDirectoryChildren(getDirectoryChildren(getDirectoryChildren(new JSONObject(response.getText())).get(0)).get(0));

		assertEquals("Directory information with depth = 3 returned too shallow", 1, depthChildren.size());

		checkDirectoryMetadata(depthChildren.get(0), "dir3", null, null, null, null, null);

		assertEquals("Directory information with depth = 3 returned too deep", 0, getDirectoryChildren(depthChildren.get(0)).size());
	}

	@Test
	public void testDirectoryWithSpaces() throws CoreException, IOException, SAXException {
		String basePath = "sampe/dir with spaces/long" + System.currentTimeMillis();
		createDirectory(basePath);

		WebRequest request = getGetFilesRequest(basePath);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	/**
	 * Tests generic file handler.
	 */
	@Test
	public void testGenericFileHandler() throws CoreException, IOException {
		String dirPath = "sample/directory/path";
		String fileName = "testGenericFileHandler-" + System.currentTimeMillis() + ".txt";
		String filePath = dirPath + "/" + fileName;

		createDirectory(dirPath);
		createFile(filePath, "Sample file content");
		String requestURI = makeResourceURIAbsolute(filePath);
		WebRequest get = new GetMethodWebRequest(requestURI);
		setAuthentication(get);
		WebResponse response = webConversation.getResource(get);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("text/plain", response.getHeaderField("Content-Type"));
	}

	/**
	 * Tests that we are not allowed to get metadata files
	 */
	@Test
	public void testGetForbiddenFiles() throws IOException, SAXException, BackingStoreException {
		//enable global anonymous read
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(ServerConstants.PREFERENCE_SCOPE);
		String oldValue = prefs.get(ServerConstants.CONFIG_FILE_ANONYMOUS_READ, null);
		prefs.put(ServerConstants.CONFIG_FILE_ANONYMOUS_READ, "true");
		prefs.flush();
		try {
			//should not be allowed to get at file root
			WebRequest request = new GetMethodWebRequest(toAbsoluteURI(FILE_SERVLET_LOCATION));
			setAuthentication(request);
			WebResponse response = webConversation.getResponse(request);
			assertEquals("Should not be able to get the root", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

			//should not be allowed to access the metadata directory
			request = new GetMethodWebRequest(toAbsoluteURI(FILE_SERVLET_LOCATION + ".metadata"));
			setAuthentication(request);
			response = webConversation.getResponse(request);
			assertEquals("Should not be able to get metadata", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());

			//should not be allowed to read specific metadata files
			request = new GetMethodWebRequest(toAbsoluteURI(FILE_SERVLET_LOCATION + ".metadata/.plugins/org.eclipse.orion.server.user.securestorage/user_store"));
			setAuthentication(request);
			response = webConversation.getResponse(request);
			assertEquals("Should not be able to get metadata", HttpURLConnection.HTTP_FORBIDDEN, response.getResponseCode());
		} finally {
			//reset the preference we messed with for the test
			if (oldValue == null)
				prefs.remove(ServerConstants.CONFIG_FILE_ANONYMOUS_READ);
			else
				prefs.put(ServerConstants.CONFIG_FILE_ANONYMOUS_READ, oldValue);
			prefs.flush();
		}
	}

	@Test
	public void testMoveFileNoOverwrite() throws Exception {
		String directoryPath = "/testMoveFile/directory/path" + System.currentTimeMillis();
		String sourcePath = directoryPath + "/source.txt";
		String destName = "destination.txt";
		String destPath = directoryPath + "/" + destName;
		createDirectory(directoryPath);
		createFile(sourcePath, "This is the contents");
		JSONObject requestObject = new JSONObject();
		addSourceLocation(requestObject, sourcePath);
		WebRequest request = getPostFilesRequest(directoryPath, requestObject.toString(), "destination.txt");
		request.setHeaderField("X-Create-Options", "move");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
		checkFileMetadata(responseObject, destName, null, null, null, null, null, null, null, null);
		assertFalse(checkFileExists(sourcePath));
		assertTrue(checkFileExists(destPath));
	}

	/**
	 * Tests renaming a file where only the case of the file name is changing.
	 * @throws Exception
	 */
	@Test
	public void testRenameFileChangeCase() throws Exception {
		String directoryPath = "/testMoveFile/directory/path" + System.currentTimeMillis();
		String sourcePath = directoryPath + "/source.txt";
		String destName = "SOURCE.txt";
		String destPath = directoryPath + "/" + destName;
		createDirectory(directoryPath);
		createFile(sourcePath, "This is the contents");
		JSONObject requestObject = new JSONObject();
		addSourceLocation(requestObject, sourcePath);
		WebRequest request = getPostFilesRequest(directoryPath, requestObject.toString(), destName);
		request.setHeaderField("X-Create-Options", "move");
		WebResponse response = webConversation.getResponse(request);
		assertTrue(response.getResponseCode() < 300);
		JSONObject responseObject = new JSONObject(response.getText());
		checkFileMetadata(responseObject, destName, null, null, null, null, null, null, null, null);
		assertTrue(checkFileExists(destPath));
	}

	@Test
	public void testMoveFileOverwrite() throws Exception {
		String directoryPath = "/testMoveFile/directory/path" + System.currentTimeMillis();
		String sourcePath = directoryPath + "/source.txt";
		String destName = "destination.txt";
		String destPath = directoryPath + "/" + destName;
		createDirectory(directoryPath);
		createFile(sourcePath, "This is the contents");
		createFile(destPath, "Original file");

		//with no-overwrite, move should fail
		JSONObject requestObject = new JSONObject();
		addSourceLocation(requestObject, sourcePath);
		WebRequest request = getPostFilesRequest(directoryPath, requestObject.toString(), "destination.txt");
		request.setHeaderField("X-Create-Options", "copy,no-overwrite");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, response.getResponseCode());

		//now omit no-overwrite and move should succeed and return 200 instead of 201
		request = getPostFilesRequest(directoryPath, requestObject.toString(), "destination.txt");
		request.setHeaderField("X-Create-Options", "move");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
		checkFileMetadata(responseObject, destName, null, null, null, null, null, null, null, null);
		assertFalse(checkFileExists(sourcePath));
		assertTrue(checkFileExists(destPath));
	}

	@Test
	public void testReadDirectory() throws CoreException, IOException, SAXException, JSONException {
		String dirName = "path" + System.currentTimeMillis();
		String directoryPath = "sample/directory/" + dirName;
		createDirectory(directoryPath);

		WebRequest request = getGetFilesRequest(directoryPath);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		JSONObject dirObject = new JSONObject(response.getText());
		checkDirectoryMetadata(dirObject, dirName, null, null, null, null, null);
	}

	@Test
	public void testReadDirectoryChildren() throws CoreException, IOException, SAXException, JSONException {
		String dirName = "path" + System.currentTimeMillis();
		String directoryPath = "sample/directory/" + dirName;
		createDirectory(directoryPath);

		String subDirectory = "subdirectory";
		createDirectory(directoryPath + "/" + subDirectory);

		String subFile = "subfile.txt";
		createFile(directoryPath + "/" + subFile, "Sample file");

		WebRequest request = getGetFilesRequest(directoryPath + "?depth=1");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));

		assertEquals("Wrong number of directory children", 2, children.size());

		for (JSONObject child : children) {
			if (child.getBoolean("Directory")) {
				checkDirectoryMetadata(child, subDirectory, null, null, null, null, null);
			} else {
				checkFileMetadata(child, subFile, null, null, null, null, null, null, null, null);
			}
		}

	}

	@Test
	public void testReadFileContents() throws CoreException, IOException, SAXException {
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		String fileName = "sampleFile" + System.currentTimeMillis() + ".txt";
		String fileContent = "Sample File Cotnent " + System.currentTimeMillis();
		createFile(directoryPath + "/" + fileName, fileContent);

		WebRequest request = getGetFilesRequest(directoryPath + "/" + fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertEquals("Invalid file content", fileContent, response.getText());
	}

	@Test
	public void testReadFileMetadata() throws Exception {
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		String fileName = "sampleFile" + System.currentTimeMillis() + ".txt";
		String fileContent = "Sample File Cotnent " + System.currentTimeMillis();
		createFile(directoryPath + "/" + fileName, fileContent);

		WebRequest request = getGetFilesRequest(directoryPath + "/" + fileName + "?parts=meta");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject result = new JSONObject(response.getText());

		assertEquals(fileName, result.optString(ProtocolConstants.KEY_NAME));

		JSONArray parents = result.optJSONArray(ProtocolConstants.KEY_PARENTS);
		assertNotNull(parents);
		assertEquals(4, parents.length());
		IPath parentPath = new Path(directoryPath);
		//immediate parent
		JSONObject parent = parents.getJSONObject(0);
		assertEquals(parentPath.segment(2), parent.getString(ProtocolConstants.KEY_NAME));
		//grandparent
		parent = parents.getJSONObject(1);
		assertEquals(parentPath.segment(1), parent.getString(ProtocolConstants.KEY_NAME));

		//ensure all parent locations end with trailing slash
		for (int i = 0; i < parents.length(); i++) {
			parent = parents.getJSONObject(i);
			String location = parent.getString(ProtocolConstants.KEY_LOCATION);
			assertTrue(location.endsWith("/"));
			location = parent.getString(ProtocolConstants.KEY_CHILDREN_LOCATION);
			URI childrenLocation = new URI(location);
			assertTrue(childrenLocation.getPath().endsWith("/"));
		}
	}

	@Test
	public void testWriteFile() throws CoreException, IOException, SAXException, JSONException {
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		String fileName = "testfile.txt";

		WebRequest request = getPostFilesRequest(directoryPath, getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//put to file location should succeed
		String location = response.getHeaderField("Location");
		String fileContent = "New contents";
		request = getPutFileRequest(location, fileContent);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		//get should return new contents
		request = getGetRequest(location);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("Invalid file content", fileContent, response.getText());
	}

	@Test
	public void testWriteFileFromURL() throws CoreException, IOException, SAXException, JSONException {
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		String fileName = "testfile.txt";

		WebRequest request = getPostFilesRequest(directoryPath, getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//put to file location should succeed
		String location = response.getHeaderField("Location");
		//just need some stable file here
		request = getPutFileRequest(location + "?source=http://eclipse.org/eclipse/project-info/home-page-one-liner.html", "");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		//get should return new contents
		request = getGetRequest(location);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("Invalid file content", "<a href=\"/eclipse/\">Eclipse Project</a>", response.getText());
	}

	@Test
	public void testWriteImageFromURL() throws CoreException, IOException, SAXException, JSONException {
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		String fileName = "testfile.gif";

		WebRequest request = getPostFilesRequest(directoryPath, getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//put to file location should succeed
		String location = response.getHeaderField("Location");
		//just need some stable file here
		request = getPutFileRequest(location + "?source=http://eclipse.org/eclipse/development/images/Adarrow.gif", "");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		//get should return new contents
		request = getGetRequest(location);
		response = webConversation.getResponse(request);
		assertEquals("image/gif", response.getHeaderField("CONTENT-TYPE"));
		assertEquals("857", response.getHeaderField("CONTENT-LENGTH"));
	}
}
