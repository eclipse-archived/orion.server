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

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.servlets.IFileStoreModificationListener.ChangeType;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class AdvancedFilesTest extends FileSystemTest {
	private JSONObject getFileMetadataObject(Boolean readonly, Boolean executable) throws JSONException {
		Map<String, Object> attributes = new HashMap<String, Object>();
		//only test attributes supported by the local file system of the test machine
		int attrs = EFS.getLocalFileSystem().attributes();
		if (readonly != null && ((attrs & EFS.ATTRIBUTE_READ_ONLY) != 0)) {
			attributes.put("ReadOnly", String.valueOf(readonly));
		}
		if (executable != null && ((attrs & EFS.ATTRIBUTE_EXECUTABLE) != 0)) {
			attributes.put("Executable", String.valueOf(executable));
		}
		JSONObject json = new JSONObject();
		json.put("Attributes", attributes);
		return json;
	}

	@Before
	public void setUp() throws Exception {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
		initializeWorkspaceLocation();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		createTestProject(testName.getMethodName());
	}

	@Test
	public void testETagDeletedFile() throws JSONException, IOException, SAXException, CoreException {
		String fileName = "testfile.txt";

		//setup: create a file
		WebRequest request = getPostFilesRequest("", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//obtain file metadata and ensure data is correct
		request = getGetFilesRequest(fileName + "?parts=meta");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String etag = response.getHeaderField(ProtocolConstants.KEY_ETAG);
		assertNotNull(etag);

		//delete the file on disk
		IFileStore fileStore = EFS.getStore(makeLocalPathAbsolute(fileName));
		fileStore.delete(EFS.NONE, null);

		//now a PUT should fail
		request = getPutFileRequest(fileName, "something");
		request.setHeaderField(ProtocolConstants.HEADER_IF_MATCH, etag);
		try {
			response = webConversation.getResponse(request);
		} catch (IOException e) {
			//inexplicably HTTPUnit throws IOException on PRECON_FAILED rather than just giving us response
			assertTrue(e.getMessage().indexOf(Integer.toString(HttpURLConnection.HTTP_PRECON_FAILED)) > 0);
		}
	}

	@Test
	public void testETagPutNotMatch() throws JSONException, IOException, SAXException, CoreException {
		String fileName = "testfile.txt";

		//setup: create a file
		WebRequest request = getPostFilesRequest("", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//obtain file metadata and ensure data is correct
		request = getGetFilesRequest(fileName + "?parts=meta");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		String etag = response.getHeaderField(ProtocolConstants.KEY_ETAG);
		assertNotNull(etag);

		//change the file on disk
		IFileStore fileStore = EFS.getStore(makeLocalPathAbsolute(fileName));
		OutputStream out = fileStore.openOutputStream(EFS.NONE, null);
		out.write("New Contents".getBytes());
		out.close();

		//now a PUT should fail
		request = getPutFileRequest(fileName, "something");
		request.setHeaderField("If-Match", etag);
		try {
			response = webConversation.getResponse(request);
		} catch (IOException e) {
			//inexplicably HTTPUnit throws IOException on PRECON_FAILED rather than just giving us response
			assertTrue(e.getMessage().indexOf(Integer.toString(HttpURLConnection.HTTP_PRECON_FAILED)) > 0);
		}
	}

	/**
	 * Test commented out due to failure
	 */
	public void _testETagHandling() throws JSONException, IOException, SAXException {
		String fileName = "testfile.txt";

		//setup: create a file
		WebRequest request = getPostFilesRequest("", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//obtain file metadata and ensure data is correct
		request = getGetFilesRequest(fileName + "?parts=meta");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No file information in response", responseObject);

		String etag1 = responseObject.getString(ProtocolConstants.KEY_ETAG);
		assertEquals(etag1, response.getHeaderField(ProtocolConstants.KEY_ETAG));

		//modify file
		request = getPutFileRequest(fileName, "something");
		request.setHeaderField("If-Match", etag1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		assertNotNull("No file information in response", responseObject);

		String etag2 = responseObject.getString(ProtocolConstants.KEY_ETAG);
		assertEquals(etag2, response.getHeaderField(ProtocolConstants.KEY_ETAG));
		// should be different as file was modified
		assertFalse(etag2.equals(etag1));

		//fetch the metadata again and ensure it is changed and correct
		request = getGetFilesRequest(fileName + "?parts=meta");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());

		String etag3 = responseObject.getString(ProtocolConstants.KEY_ETAG);
		assertEquals(etag3, response.getHeaderField(ProtocolConstants.KEY_ETAG));
		assertEquals(etag2, etag3);
	}

	@Test
	public void testGetNonExistingFile() throws IOException, SAXException {
		WebRequest request = getGetFilesRequest("does/not/exists/directory");
		WebResponse response = webConversation.getResponse(request);
		assertEquals("Retriving non existing directory return wrong response code.", HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		request = getGetFilesRequest("does/not/exists/directory?depth=5");
		response = webConversation.getResponse(request);
		assertEquals("Retriving non existing directory return wrong response code.", HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

	@Test
	public void testMetadataHandling() throws JSONException, IOException, SAXException {

		String fileName = "testMetadataHandling.txt";

		//setup: create a file
		WebRequest request = getPostFilesRequest("", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//obtain file metadata and ensure data is correct
		request = getGetFilesRequest(fileName + "?parts=meta");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No file information in response", responseObject);
		checkFileMetadata(responseObject, fileName, new Long(-1), null, null, request.getURL().getRef(), new Long(0), null, null, null);

		//modify the metadata
		request = getPutFileRequest(fileName + "?parts=meta", getFileMetadataObject(true, true).toString());
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getResponseCode());

		//fetch the metadata again and ensure it is changed
		request = getGetFilesRequest(fileName + "?parts=meta");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		checkFileMetadata(responseObject, fileName, new Long(-1), null, null, request.getURL().getRef(), new Long(0), new Boolean(true), new Boolean(true), null);

		//make the file writeable again so test can clean up
		request = getPutFileRequest(fileName + "?parts=meta", getFileMetadataObject(false, false).toString());
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getResponseCode());

	}

	/**
	 * @returns A Patch with a single diff 
	 */
	private JSONObject createPatch(int start, int end, String text) throws JSONException {
		JSONObject diff = new JSONObject();
		diff.put("start", start);
		diff.put("end", end);
		diff.put("text", text);

		JSONArray diffArray = new JSONArray();
		diffArray.put(diff);

		JSONObject patch = new JSONObject();
		patch.put("diff", diffArray);
		return patch;
	}

	@Test
	public void testPatchEmptyFile() throws JSONException, IOException, SAXException {
		String fileName = "testPatch.txt";
		String patchedContents = "hi there";

		//setup: create an empty file
		WebRequest request = getPostFilesRequest("", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//patch it, ensure the metadata is returned and is correct
		JSONObject patch = createPatch(0, 0, patchedContents);
		request = getPatchFileRequest(fileName, patch.toString());
		response = webConversation.getResponse(request);
		JSONObject patchedMetadata = new JSONObject(response.getText());
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("Has expected content type", "application/json", response.getContentType());
		assertEquals("Has expected length", patchedContents.length(), patchedMetadata.getInt(ProtocolConstants.KEY_LENGTH));

		//fetch metadata, make sure it is consistent with metadata returned from patch response.
		request = getGetFilesRequest(fileName + "?parts=meta");
		response = webConversation.getResponse(request);
		JSONObject getMetadata = new JSONObject(response.getText());
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("ETag is consistent", patchedMetadata.getString(ProtocolConstants.KEY_ETAG), getMetadata.getString(ProtocolConstants.KEY_ETAG));

		//fetch contents, make sure they are correct
		request = getGetFilesRequest(fileName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals("Has expected contents", patchedContents, response.getText());
	}

	@Test
	public void testListenerMetadataHandling() throws JSONException, IOException, SAXException, CoreException {

		String fileName = "testListenerMetadataHandling.txt";

		//setup: create a file
		WebRequest request = getPostFilesRequest("", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		TestFilesystemModificationListener l = new TestFilesystemModificationListener();
		try {
			//modify the metadata
			request = getPutFileRequest(fileName + "?parts=meta", getFileMetadataObject(true, true).toString());
			response = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getResponseCode());

			IFileStore fileStore = EFS.getStore(makeLocalPathAbsolute(fileName));
			l.assertListenerNotified(fileStore, ChangeType.PUTINFO);
		} finally {
			TestFilesystemModificationListener.cleanup(l);
		}

		//make the file writeable again so test can clean up
		request = getPutFileRequest(fileName + "?parts=meta", getFileMetadataObject(false, false).toString());
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getResponseCode());

	}

}
