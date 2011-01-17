/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.xfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.meterware.httpunit.*;
import java.io.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.junit.*;
import org.xml.sax.SAXException;

/**
 * 
 */
public class ImportTest extends FileSystemTest {
	WebConversation webConversation;

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	@After
	public void removeTempDir() throws CoreException {
		remove("sample");
	}

	@Before
	public void setUp() throws CoreException {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
	}

	@Test
	public void testImportZip() throws CoreException, IOException, SAXException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		//start the import
		File source = new File(ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/client.zip").getPath());
		long length = source.length();
		PostMethodWebRequest request = new PostMethodWebRequest(ServerTestsActivator.getServerLocation() + "/xfer/" + directoryPath);
		request.setHeaderField("X-Xfer-Content-Length", Long.toString(length));
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		assertEquals(200, postResponse.getResponseCode());
		String location = postResponse.getHeaderField("Location");
		assertNotNull(location);

		//repeat putting chunks until done
		byte[] chunk = new byte[1024];
		InputStream in = new BufferedInputStream(new FileInputStream(source));
		int chunkSize = 0;
		int totalTransferred = 0;
		while ((chunkSize = in.read(chunk, 0, chunk.length)) > 0) {
			PutMethodWebRequest put = new PutMethodWebRequest(location, new ByteArrayInputStream(chunk, 0, chunkSize), "application/zip");
			put.setHeaderField("Content-Range", "bytes " + totalTransferred + "-" + totalTransferred + chunkSize + "/" + length);
			setAuthentication(put);
			totalTransferred += chunkSize;
			WebResponse putResponse = webConversation.getResponse(put);
			if (totalTransferred == length) {
				assertEquals(201, putResponse.getResponseCode());
			} else {
				assertEquals(308, putResponse.getResponseCode());
				String range = putResponse.getHeaderField("Range");
				assertEquals("0-" + totalTransferred, range);
			}

		}

		//assert the file is present in the workspaces
		checkFileExists(directoryPath + "/client.zip");
	}
}
