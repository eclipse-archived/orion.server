/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
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
import static org.junit.Assert.assertNotNull;

import com.meterware.httpunit.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.runtime.CoreException;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

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
		if (readonly != null) {
			attributes.put("ReadOnly", String.valueOf(readonly));
		}
		if (executable != null) {
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

		//modify the metadata and ensure operation succeeded
		request = getPutFilesRequest(fileName + "?parts=meta", getFileMetadataObject(true, true).toString());
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		checkFileMetadata(responseObject, fileName, new Long(-1), null, null, request.getURL().getRef(), new Long(0), new Boolean(true), new Boolean(true));

	}

}
