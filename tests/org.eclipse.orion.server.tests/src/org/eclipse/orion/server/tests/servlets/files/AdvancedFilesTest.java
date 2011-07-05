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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class AdvancedFilesTest extends FileSystemTest {
	@Before
	public void prepereEmptyEnviroment() throws CoreException {
		clearWorkspace();
	}

	@Test
	public void testGetNonExistingFile() throws IOException, SAXException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		WebRequest request = getGetFilesRequest("does/not/exists/directory");
		WebResponse response = webConversation.getResponse(request);
		assertEquals("Retriving non existing directory return wrong response code.", HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());

		request = getGetFilesRequest("does/not/exists/directory?depth=5");
		response = webConversation.getResponse(request);
		assertEquals("Retriving non existing directory return wrong response code.", HttpURLConnection.HTTP_NOT_FOUND, response.getResponseCode());
	}

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

	@Test
	public void testMetadataHandling() throws JSONException, IOException, SAXException {

		String fileName = "testfile.txt";

		//setup: create a file
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		WebRequest request = getPostFilesRequest("/", getNewFileJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());

		//obtain file metadata and ensure data is correct
		request = getGetFilesRequest(fileName + "?parts=meta");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject responseObject = new JSONObject(response.getText());
		assertNotNull("No file information in response", responseObject);
		checkFileMetadata(responseObject, fileName, new Long(-1), null, null, request.getURL().getRef(), new Long(0), null, null);

		//modify the metadata
		request = getPutFilesRequest(fileName + "?parts=meta", getFileMetadataObject(true, true).toString());
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getResponseCode());

		//fetch the metadata again and ensure it is changed
		request = getGetFilesRequest(fileName + "?parts=meta");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		responseObject = new JSONObject(response.getText());
		checkFileMetadata(responseObject, fileName, new Long(-1), null, null, request.getURL().getRef(), new Long(0), new Boolean(true), new Boolean(true));

	}

	@Test
	public void testETagHandling() throws JSONException, IOException, SAXException {
		String fileName = "testfile.txt";

		//setup: create a file
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		WebRequest request = getPostFilesRequest("/", getNewFileJSON(fileName).toString(), fileName);
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

}
