/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.files;

import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.tests.AbstractServerTest;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebRequest;

/**
 * Common base class for file system tests.
 */
public abstract class FileSystemTest extends AbstractServerTest {

	public static final String FILE_SERVLET_LOCATION = Activator.LOCATION_FILE_SERVLET + '/';
	private static String FILESTORE_PREFIX;
	//workspace or any files prefix, please follow with '/' if not empty.
	private static final String RUNTIME_WORKSPACE = "";

	public static final String SERVER_LOCATION = ServerTestsActivator.getServerLocation();

	protected static boolean checkDirectoryExists(String path) throws CoreException {
		IFileStore dir = EFS.getStore(URI.create(FILESTORE_PREFIX + path));
		return (dir.fetchInfo().exists() && dir.fetchInfo().isDirectory());
	}

	protected static boolean checkFileExists(String path) throws CoreException {
		IFileStore file = EFS.getStore(URI.create(FILESTORE_PREFIX + path));
		return (file.fetchInfo().exists() && !file.fetchInfo().isDirectory());
	}

	protected static void clearWorkspace() throws CoreException {
		IFileStore workspaceDir = EFS.getStore(URI.create(FILESTORE_PREFIX));
		//delete all projects
		for (IFileStore child : workspaceDir.childStores(EFS.NONE, null)) {
			if (!".metadata".equals(child.getName()))
				child.delete(EFS.NONE, null);
		}
		//delete workspace metadata
		IFileStore metaDir = workspaceDir.getFileStore(new Path(".metadata/.plugins/org.eclipse.orion.server.core/.settings"));
		metaDir.delete(EFS.NONE, null);
		workspaceDir.mkdir(EFS.NONE, null);
	}

	protected static void createDirectory(String path) throws CoreException {
		IFileInfo info = null;
		try {
			IFileStore dir = EFS.getStore(URIUtil.fromString(FILESTORE_PREFIX + path));
			dir.mkdir(EFS.NONE, null);
			info = dir.fetchInfo();
		} catch (URISyntaxException e) {
			assertTrue(e.getMessage(), false);
		}
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

	protected static void createFile(String path, String fileContent) throws CoreException {
		createFile(URI.create(FILESTORE_PREFIX + path), fileContent);
	}

	protected static void initializeWorkspaceLocation() {
		FILESTORE_PREFIX = Activator.getDefault().getRootLocationURI().toString() + "/";
	}

	protected static void remove(String path) throws CoreException {
		IFileStore outputFile = EFS.getStore(URI.create(FILESTORE_PREFIX + path));
		outputFile.delete(EFS.NONE, null);
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

	protected void checkFileMetadata(JSONObject fileObject, String name, Long localTimestamp, String charset, String contentType, String location, Long length, Boolean isReadonly, Boolean isExecutable) throws JSONException {

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
	}

	protected WebRequest getDeleteFilesRequest(String uri) {
		try {
			WebRequest request = new DeleteMethodWebRequest(makeAbsolute(uri));
			request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
			setAuthentication(request);
			return request;
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
		//can never get here
		return null;
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
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else if (location.startsWith("/"))
			requestURI = SERVER_LOCATION + location;
		else
			requestURI = SERVER_LOCATION + FILE_SERVLET_LOCATION + RUNTIME_WORKSPACE + location;
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
		json.put(ProtocolConstants.KEY_NAME, dirName);
		json.put(ProtocolConstants.KEY_DIRECTORY, false);
		json.put("Charset", "UTF-8");
		json.put("ContentType", "text/plain");
		return json;
	}

	protected WebRequest getPostFilesRequest(String uri, String json, String slug) {
		try {
			WebRequest request = new PostMethodWebRequest(makeAbsolute(uri), getJsonAsStream(json), "application/json");
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, slug);
			request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
			setAuthentication(request);
			return request;
		} catch (UnsupportedEncodingException e) {
			fail(e.getMessage());
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
		//can never get here
		return null;
	}

	protected WebRequest getPutFilesRequest(String uri, String json) {
		try {
			WebRequest request = new PutMethodWebRequest(makeAbsolute(uri), getJsonAsStream(json), "application/json");
			request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
			setAuthentication(request);
			return request;
		} catch (UnsupportedEncodingException e) {
			fail(e.getMessage());
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
		//can never get here
		return null;
	}

	protected WebRequest getPutFileRequest(String uri, String body) {
		try {
			WebRequest request = new PutMethodWebRequest(makeAbsolute(uri), getJsonAsStream(body), "application/json");
			request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
			setAuthentication(request);
			return request;
		} catch (UnsupportedEncodingException e) {
			fail(e.getMessage());
		} catch (URISyntaxException e) {
			fail(e.getMessage());
		}
		//can never get here
		return null;
	}

	/**
	 * Makes a URI absolute. If the provided URI is relative, it is assumed to be relative to the workspace location (file servlet location).
	 * If the provided URI is already absolute it is returned as-is
	 * @throws URISyntaxException 
	 */
	protected String makeAbsolute(String uriString) throws URISyntaxException {
		URI uri = new URI(uriString);
		if (uri.isAbsolute())
			return uriString;
		if (uriString.startsWith(FILE_SERVLET_LOCATION))
			return new URI(SERVER_LOCATION + uriString).toString();
		//avoid double slash
		if (uriString.startsWith("/"))
			uriString = uriString.substring(1);
		return new URI(SERVER_LOCATION + FILE_SERVLET_LOCATION + uriString).toString();
	}

	protected WebRequest getCreateWorkspaceRequest(String workspaceName) {
		WebRequest request = new PostMethodWebRequest(SERVER_LOCATION + "/workspace");
		if (workspaceName != null)
			request.setHeaderField(ProtocolConstants.HEADER_SLUG, workspaceName);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

}
