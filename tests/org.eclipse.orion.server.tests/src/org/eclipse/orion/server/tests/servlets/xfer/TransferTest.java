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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.junit.*;
import org.xml.sax.SAXException;

/**
 * 
 */
public class TransferTest extends FileSystemTest {
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
	public void testImportAndUnzip() throws CoreException, IOException, SAXException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		//start the import
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/client.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		PostMethodWebRequest request = new PostMethodWebRequest(ServerTestsActivator.getServerLocation() + "/xfer/" + directoryPath);
		request.setHeaderField("X-Xfer-Content-Length", Long.toString(length));
		request.setHeaderField("X-Xfer-Options", "unzip");
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		assertEquals(200, postResponse.getResponseCode());
		String location = postResponse.getHeaderField("Location");
		assertNotNull(location);

		doImport(source, length, location);

		//assert the file has been unzipped in the workspace
		assertTrue(checkFileExists(directoryPath + "/org.eclipse.e4.webide/static/js/navigate-tree/navigate-tree.js"));
	}

	@Test
	public void testImportFile() throws CoreException, IOException, SAXException {
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

		doImport(source, length, location);

		//assert the file is present in the workspace
		assertTrue(checkFileExists(directoryPath + "/client.zip"));
	}

	@Test
	public void testExportProject() throws CoreException, IOException, SAXException {
		//create content to export
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		String fileContents = "This is the file contents";
		createFile(directoryPath + "/file.txt", fileContents);

		GetMethodWebRequest export = new GetMethodWebRequest(ServerTestsActivator.getServerLocation() + "/xfer/export/" + directoryPath + ".zip");
		setAuthentication(export);
		WebResponse response = webConversation.getResponse(export);
		assertEquals(200, response.getResponseCode());
		boolean found = false;
		ZipInputStream in = new ZipInputStream(response.getInputStream());
		ZipEntry entry;
		while ((entry = in.getNextEntry()) != null) {
			if (entry.getName().equals("file.txt")) {
				found = true;
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				IOUtilities.pipe(in, bytes, false, false);
				assertEquals(fileContents, new String(bytes.toByteArray()));
			}
		}
		assertTrue(found);
	}

	private void doImport(File source, long length, String location) throws FileNotFoundException, IOException, SAXException {
		//repeat putting chunks until done
		byte[] chunk = new byte[64 * 1024];
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
	}
}
