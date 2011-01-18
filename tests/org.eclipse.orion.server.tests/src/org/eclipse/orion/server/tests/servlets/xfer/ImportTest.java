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
import static org.junit.Assert.assertTrue;

import com.meterware.httpunit.*;
import java.io.*;
import java.net.URL;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
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
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/client.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		PostMethodWebRequest request = new PostMethodWebRequest(ServerTestsActivator.getServerLocation() + "/xfer/" + directoryPath);
		request.setHeaderField("X-Xfer-Content-Length", Long.toString(length));
		request.setHeaderField("Slug", "client.zip");
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		assertEquals(200, postResponse.getResponseCode());
		String location = postResponse.getHeaderField("Location");
		assertNotNull(location);

		//repeat putting chunks until done
		byte[] chunk = new byte[16 * 1024];
		InputStream in = new BufferedInputStream(new FileInputStream(source));
		int chunkSize = 0;
		int totalTransferred = 0;
		while ((chunkSize = in.read(chunk, 0, chunk.length)) > 0) {
			PutMethodWebRequest put = new PutMethodWebRequest(location, new ByteArrayInputStream(chunk, 0, chunkSize), "application/zip");
			put.setHeaderField("Content-Range", "bytes " + totalTransferred + "-" + (totalTransferred + chunkSize - 1) + "/" + length);
			put.setHeaderField("Content-Length", "" + length);
			put.setHeaderField("Content-Type", "application/zip");
			setAuthentication(put);
			totalTransferred += chunkSize;
			WebResponse putResponse = webConversation.getResponse(put);
			if (totalTransferred == length) {
				assertEquals(201, putResponse.getResponseCode());
			} else {
				assertEquals(308, putResponse.getResponseCode());
				String range = putResponse.getHeaderField("Range");
				assertEquals("bytes 0-" + (totalTransferred - 1), range);
			}

		}

		//assert the file is present in the workspace
		assertTrue(checkFileExists(directoryPath + "/client.zip"));
	}
}
