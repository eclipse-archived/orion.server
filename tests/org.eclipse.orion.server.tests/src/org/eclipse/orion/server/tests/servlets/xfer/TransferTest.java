/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.servlets.Slug;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.HttpUnitOptions;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebResponse;

/**
 * 
 */
public class TransferTest extends FileSystemTest {

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	private void doImport(File source, long length, String location) throws FileNotFoundException, IOException, SAXException {
		doImport(source, length, location, "application/zip");
	}

	private void doImport(File source, long length, String location, String contentType) throws FileNotFoundException, IOException, SAXException {
		if (source.length() == 0) {
			PutMethodWebRequest put = new PutMethodWebRequest(location, new ByteArrayInputStream(new byte[0], 0, 0), contentType);
			put.setHeaderField("Content-Range", "bytes 0-0/0");
			put.setHeaderField("Content-Length", "0");
			put.setHeaderField("Content-Type", "application/zip");
			setAuthentication(put);
			WebResponse putResponse = webConversation.getResponse(put);
			assertEquals(HttpURLConnection.HTTP_CREATED, putResponse.getResponseCode());
			return;
		}
		//repeat putting chunks until done
		byte[] chunk = new byte[64 * 1024];
		InputStream in = new BufferedInputStream(new FileInputStream(source));
		int chunkSize = 0;
		int totalTransferred = 0;
		while ((chunkSize = in.read(chunk, 0, chunk.length)) > 0) {
			byte[] content = getContent(chunk, chunkSize, contentType);
			PutMethodWebRequest put = new PutMethodWebRequest(location, new ByteArrayInputStream(content), contentType);
			put.setHeaderField("Content-Range", "bytes " + totalTransferred + "-" + (totalTransferred + chunkSize - 1) + "/" + length);
			put.setHeaderField("Content-Length", "" + content.length);
			put.setHeaderField("Content-Type", contentType);
			setAuthentication(put);
			totalTransferred += chunkSize;
			WebResponse putResponse = webConversation.getResponse(put);
			if (totalTransferred == length) {
				assertEquals(HttpURLConnection.HTTP_CREATED, putResponse.getResponseCode());
			} else {
				assertEquals(308, putResponse.getResponseCode());//308 = Permanent Redirect
				String range = putResponse.getHeaderField("Range");
				assertEquals("bytes 0-" + (totalTransferred - 1), range);
			}
		}
		in.close();
	}

	private byte[] getContent(byte[] chunk, int chunkSize, String contentType) throws IOException {
		if (!contentType.startsWith("multipart/")) {
			return Arrays.copyOf(chunk, chunkSize);
		}
		int boundaryOff = contentType.indexOf("boundary=");
		String boundary = contentType.substring(boundaryOff + 9);
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		bout.write("PREAMBLE\r\n\r\nPREAMBLEr\n".getBytes("ISO-8859-1"));
		bout.write("--".getBytes("ISO-8859-1"));
		bout.write(boundary.getBytes("ISO-8859-1"));
		bout.write("\r\n".getBytes("ISO-8859-1"));
		bout.write("Content-type: text/plain; charset=us-ascii\r\n".getBytes("ISO-8859-1"));
		bout.write("\r\n".getBytes("ISO-8859-1")); // empty line separates chunk header from body
		bout.write(chunk, 0, chunkSize);
		bout.write("\r\n--".getBytes("ISO-8859-1"));
		bout.write(boundary.getBytes("ISO-8859-1"));
		bout.write("--\r\n".getBytes("ISO-8859-1"));
		bout.write("EPILOGUE".getBytes("ISO-8859-1"));
		return bout.toByteArray();
	}

	/**
	 * Returns the URI of an import HTTP request on the given directory.
	 */
	private String getImportRequestPath(String directoryPath) {
		IPath path = new Path("/xfer/import").append(getTestBaseResourceURILocation()).append(directoryPath);
		return SERVER_LOCATION + path.toString();
	}

	/**
	 * Returns the URI of an export HTTP request on the given directory.
	 */
	private String getExportRequestPath(String directoryPath) {
		IPath path = new Path("/xfer/export").append(getTestBaseResourceURILocation()).append(directoryPath).removeTrailingSeparator().addFileExtension("zip");
		return SERVER_LOCATION + path.toString();
	}

	@After
	public void tearDown() throws CoreException {
		remove("sample");
	}

	@Before
	public void setUp() throws Exception {
		HttpUnitOptions.setDefaultCharacterSet("UTF-8");
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		createTestProject(testName.getMethodName());
	}

	@Test
	public void testExportProject() throws CoreException, IOException, SAXException {
		//create content to export
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		createDirectory(directoryPath + "/empty");
		String fileContents = "This is the file contents";
		createFile(directoryPath + "/file.txt", fileContents);

		GetMethodWebRequest export = new GetMethodWebRequest(getExportRequestPath(directoryPath));
		setAuthentication(export);
		WebResponse response = webConversation.getResponse(export);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		boolean found = false;
		boolean emptyDirFound = false;
		ZipInputStream in = new ZipInputStream(response.getInputStream());
		ZipEntry entry;
		while ((entry = in.getNextEntry()) != null) {
			if (entry.getName().equals("file.txt")) {
				found = true;
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				IOUtilities.pipe(in, bytes, false, false);
				assertEquals(fileContents, new String(bytes.toByteArray()));
			} else if (entry.getName().equals("empty/") && entry.isDirectory()) {
				emptyDirFound = true;
			}
		}
		assertTrue(found && emptyDirFound);
	}

	/**
	 * Tests importing a zip file from a remote URL, and verifying that it is imported
	 */
//	@Test
//	public void testImportFromURL() throws CoreException, IOException, SAXException {
//		//just a known zip file that we can use for testing that is stable
//		String sourceZip = "http://eclipse.org/eclipse/platform-core/downloads/tools/org.eclipse.core.tools.restorer_3.0.0.zip";
//
//		//create a directory to upload to
//		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
//		createDirectory(directoryPath);
//
//		//start the import
//		String requestPath = getImportRequestPath(directoryPath) + "?source=" + sourceZip;
//		PostMethodWebRequest request = new PostMethodWebRequest(requestPath);
//		setAuthentication(request);
//		request.setHeaderField(ProtocolConstants.HEADER_XFER_OPTIONS, "raw");
//		WebResponse postResponse = webConversation.getResponse(request);
//		assertEquals(HttpURLConnection.HTTP_CREATED, postResponse.getResponseCode());
//		String location = postResponse.getHeaderField("Location");
//		assertNotNull(location);
//
//		//assert the file has been imported but not unzipped
//		assertTrue(checkFileExists(directoryPath + "/org.eclipse.core.tools.restorer_3.0.0.zip"));
//	}

	/**
	 * Tests importing a zip file from a remote URL, and verifying that it is imported and unzipped.
	 */
//	@Test
//	public void testImportAndUnzipFromURL() throws CoreException, IOException, SAXException {
//		//just a known zip file that we can use for testing that is stable
//		String sourceZip = "http://eclipse.org/eclipse/platform-core/downloads/tools/org.eclipse.core.tools.restorer_3.0.0.zip";
//
//		//create a directory to upload to
//		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
//		createDirectory(directoryPath);
//
//		//start the import
//		String requestPath = getImportRequestPath(directoryPath) + "?source=" + sourceZip;
//		PostMethodWebRequest request = new PostMethodWebRequest(requestPath);
//		setAuthentication(request);
//		WebResponse postResponse = webConversation.getResponse(request);
//		assertEquals(HttpURLConnection.HTTP_CREATED, postResponse.getResponseCode());
//		String location = postResponse.getHeaderField("Location");
//		assertNotNull(location);
//
//		//assert the file has been unzipped in the workspace
//		assertTrue(checkFileExists(directoryPath + "/org.eclipse.core.tools.restorer_3.0.0/org.eclipse.core.tools.restorer_3.0.0.200607121527.jar"));
//	}

	/**
	 * Tests importing from a URL, where the source URL is not absolute. This should fail.
	 */
	@Test
	public void testImportFromURLMalformed() throws CoreException, IOException, SAXException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		//start the import
		String requestPath = getImportRequestPath(directoryPath) + "?source=pumpkins";
		PostMethodWebRequest request = new PostMethodWebRequest(requestPath);
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, postResponse.getResponseCode());
	}

	@Test
	public void testImportAndUnzip() throws CoreException, IOException, SAXException, URISyntaxException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		//start the import
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/client.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		String importPath = getImportRequestPath(directoryPath);
		PostMethodWebRequest request = new PostMethodWebRequest(importPath);
		request.setHeaderField("X-Xfer-Content-Length", Long.toString(length));
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, postResponse.getResponseCode());
		String location = postResponse.getHeaderField("Location");
		assertNotNull(location);
		URI importURI = URIUtil.fromString(importPath);
		location = importURI.resolve(location).toString();
		doImport(source, length, location);

		//assert the file has been unzipped in the workspace
		assertTrue(checkFileExists(directoryPath + "/org.eclipse.e4.webide/static/js/navigate-tree/navigate-tree.js"));
	}

	@Test
	public void testImportFile() throws CoreException, IOException, SAXException, URISyntaxException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		//start the import
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/client.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		String importPath = getImportRequestPath(directoryPath);
		PostMethodWebRequest request = new PostMethodWebRequest(importPath);
		request.setHeaderField("X-Xfer-Content-Length", Long.toString(length));
		request.setHeaderField("X-Xfer-Options", "raw");

		request.setHeaderField("Slug", "client.zip");
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, postResponse.getResponseCode());
		String location = postResponse.getHeaderField("Location");
		assertNotNull(location);
		URI importURI = URIUtil.fromString(importPath);
		location = importURI.resolve(location).toString();

		doImport(source, length, location);

		//assert the file is present in the workspace
		assertTrue(checkFileExists(directoryPath + "/client.zip"));
		//assert that imported file has same content as original client.zip
		assertTrue(checkContentEquals(source, directoryPath + "/client.zip"));
	}

	/**
	 * Creates a temporary file, optionally writing the given contents to it.
	 * 
	 * By creating test artifacts that contain DBCS characters in the filename at runtime, instead of storing
	 * them in the repo, we avoid issues with EGit on Mac OS X. Workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=434337
	 * 
	 * Caller is responsible for deleting the returned file when it is no longer needed.
	 * @param filename
	 * @param contents File contents (UTF-8)
	 * @return The temp file
	 */
	private File createTempFile(String filename, String contents) throws IOException {
		File tempFile = File.createTempFile(filename, null);
		if (contents != null) {
			IOUtilities.pipe(IOUtilities.toInputStream(contents), new FileOutputStream(tempFile), true, true);
		}
		return tempFile;
	}

	@Test
	public void testImportEmojiFilename() throws CoreException, IOException, SAXException, URISyntaxException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		//start the import
		// file with Emoji characters in the filename.
		// U+1F60A: SMILING FACE WITH SMILING EYES ("\ud83d\ude0a")
		// U+1F431: CAT FACE ("\ud83d\udc31")
		// U+1F435: MONKEY FACE ("\ud83d\udc35")
		String filename = "\ud83d\ude0a\ud83d\udc31\ud83d\udc35.txt";
		String contents = "Emoji characters: \ud83d\ude0a\ud83d\udc31\ud83d\udc35";
		File source = null;
		try {
			source = createTempFile(filename, contents);
			long length = source.length();

			String importPath = getImportRequestPath(directoryPath);
			PostMethodWebRequest request = new PostMethodWebRequest(importPath);
			request.setHeaderField("X-Xfer-Content-Length", Long.toString(length));
			request.setHeaderField("X-Xfer-Options", "raw");

			request.setHeaderField("Slug", Slug.encode(filename));
			setAuthentication(request);
			WebResponse postResponse = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, postResponse.getResponseCode());
			String location = postResponse.getHeaderField("Location");
			assertNotNull(location);
			URI importURI = URIUtil.fromString(importPath);
			location = importURI.resolve(location).toString();

			doImport(source, length, location, "text/plain");

			//assert the file is present in the workspace
			assertTrue(checkFileExists(directoryPath + File.separator + filename));
			//assert that imported file has same content as original client.zip
			assertTrue(checkContentEquals(source, directoryPath + File.separator + filename));
		} finally {
			// Delete the temp file
			FileUtils.deleteQuietly(source);
		}
	}

	@Test
	public void testImportDBCSFilename() throws CoreException, IOException, SAXException, URISyntaxException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		//start the import
		// file with DBCS character in the filename.
		String filename = "\u3042.txt";
		File source = null;
		try {
			source = createTempFile(filename, "No content");
			long length = source.length();
			String importPath = getImportRequestPath(directoryPath);
			PostMethodWebRequest request = new PostMethodWebRequest(importPath);
			request.setHeaderField("X-Xfer-Content-Length", Long.toString(length));
			request.setHeaderField("X-Xfer-Options", "raw");

			request.setHeaderField("Slug", Slug.encode(filename));
			setAuthentication(request);
			WebResponse postResponse = webConversation.getResponse(request);
			assertEquals(HttpURLConnection.HTTP_OK, postResponse.getResponseCode());
			String location = postResponse.getHeaderField("Location");
			assertNotNull(location);
			URI importURI = URIUtil.fromString(importPath);
			location = importURI.resolve(location).toString();

			doImport(source, length, location, "text/plain");

			//assert the file is present in the workspace
			assertTrue(checkFileExists(directoryPath + File.separator + filename));
			//assert that imported file has same content as original client.zip
			assertTrue(checkContentEquals(source, directoryPath + File.separator + filename));
		} finally {
			// Delete the temp file
			FileUtils.deleteQuietly(source);
		}
	}

	@Test
	public void testImportFileMultiPart() throws CoreException, IOException, SAXException, URISyntaxException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/client.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());

		// current server implementation cannot handle binary data, thus base64-encode client.zip before import
		File expected = EFS.getStore(makeLocalPathAbsolute(directoryPath + "/expected.txt")).toLocalFile(EFS.NONE, null);
		byte[] expectedContent = Base64.encode(FileUtils.readFileToByteArray(source));
		FileUtils.writeByteArrayToFile(expected, expectedContent);

		//start the import
		long length = expectedContent.length;
		String importPath = getImportRequestPath(directoryPath);
		PostMethodWebRequest request = new PostMethodWebRequest(importPath);
		request.setHeaderField("X-Xfer-Content-Length", Long.toString(length));
		request.setHeaderField("X-Xfer-Options", "raw");
		request.setHeaderField("Slug", "actual.txt");
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, postResponse.getResponseCode());
		String location = postResponse.getHeaderField("Location");
		assertNotNull(location);
		URI importURI = URIUtil.fromString(importPath);
		location = importURI.resolve(location).toString();

		// perform the upload
		doImport(expected, length, location, "multipart/mixed;boundary=foobar");

		//assert the file is present in the workspace
		assertTrue(checkFileExists(directoryPath + "/actual.txt"));
		//assert that actual.txt has same content as expected.txt
		assertTrue(checkContentEquals(expected, directoryPath + "/actual.txt"));
	}

	/**
	 * Tests attempting to import and unzip a file that is not a zip file.
	 */
	@Test
	public void testImportUnzipNonZipFile() throws CoreException, IOException, SAXException {
		//create a directory to upload to
		String directoryPath = "sample/testImportWithPost/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		//start the import
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/junk.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		InputStream in = new BufferedInputStream(new FileInputStream(source));
		PostMethodWebRequest request = new PostMethodWebRequest(getImportRequestPath(directoryPath), in, "application/zip");
		request.setHeaderField("Content-Length", "" + length);
		request.setHeaderField("Content-Type", "application/zip");
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, postResponse.getResponseCode());
	}

	@Test
	public void testImportWithPostZeroByteFile() throws CoreException, IOException, SAXException, URISyntaxException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		//start the import
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/zeroByteFile.txt");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		assertEquals(length, 0);
		String importPath = getImportRequestPath(directoryPath);
		PostMethodWebRequest request = new PostMethodWebRequest(importPath);
		request.setHeaderField("X-Xfer-Content-Length", Long.toString(length));
		request.setHeaderField("X-Xfer-Options", "raw");

		request.setHeaderField("Slug", "zeroByteFile.txt");
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, postResponse.getResponseCode());
		String location = postResponse.getHeaderField("Location");
		assertNotNull(location);
		URI importURI = URIUtil.fromString(importPath);
		location = importURI.resolve(location).toString();

		doImport(source, length, location, "text/plain");

		//assert the file is present in the workspace
		assertTrue(checkFileExists(directoryPath + "/zeroByteFile.txt"));
	}

	@Test
	public void testImportWithPost() throws CoreException, IOException, SAXException {
		//create a directory to upload to
		String directoryPath = "sample/testImportWithPost/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		//start the import
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/client.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		InputStream in = new BufferedInputStream(new FileInputStream(source));
		PostMethodWebRequest request = new PostMethodWebRequest(getImportRequestPath(directoryPath), in, "application/zip");
		request.setHeaderField("Content-Length", "" + length);
		request.setHeaderField("Content-Type", "application/zip");
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, postResponse.getResponseCode());
		String location = postResponse.getHeaderField("Location");
		assertNotNull(location);
		String type = postResponse.getHeaderField("Content-Type");
		assertNotNull(type);
		assertTrue(type.contains("text/html"));

		//assert the file has been unzipped in the workspace
		assertTrue(checkFileExists(directoryPath + "/org.eclipse.e4.webide/static/js/navigate-tree/navigate-tree.js"));
	}

	@Test
	public void testImportFilesWithoutOverride() throws CoreException, IOException, SAXException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		//create a file that is not overwritten
		String fileContents = "This is the file contents";
		createFile(directoryPath + "/client.zip", fileContents);
		//start the import
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/client.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		InputStream in = new BufferedInputStream(new FileInputStream(source));
		PostMethodWebRequest request = new PostMethodWebRequest(getImportRequestPath(directoryPath), in, "application/zip");
		request.setHeaderField("Content-Length", "" + length);
		request.setHeaderField("Content-Type", "application/octet-stream");
		request.setHeaderField("Slug", "client.zip");
		request.setHeaderField("X-Xfer-Options", "raw,no-overwrite");
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		//assert the server rejects the override
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, postResponse.getResponseCode());
		//assert the file is still present in the workspace
		assertTrue(checkFileExists(directoryPath + "/client.zip"));
		//assert that imported file was not overwritten
		assertTrue(checkContentEquals(createTempFile("expectedFile", fileContents), directoryPath + "/client.zip"));
	}

	@Test
	public void testImportFilesWithOverride() throws CoreException, IOException, SAXException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);
		//create a file that is to be overwritten
		String fileContents = "This is the file contents";
		createFile(directoryPath + "/client.zip", fileContents);
		//start the import
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/client.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		InputStream in = new BufferedInputStream(new FileInputStream(source));
		PostMethodWebRequest request = new PostMethodWebRequest(getImportRequestPath(directoryPath), in, "application/zip");
		request.setHeaderField("Content-Length", "" + length);
		request.setHeaderField("Content-Type", "application/octet-stream");
		request.setHeaderField("Slug", "client.zip");
		request.setHeaderField("X-Xfer-Options", "raw,overwrite-older");
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		//assert the server accepts the override
		assertEquals(HttpURLConnection.HTTP_CREATED, postResponse.getResponseCode());
		String location = postResponse.getHeaderField("Location");
		assertNotNull(location);
		String type = postResponse.getHeaderField("Content-Type");
		assertNotNull(type);
		//assert the file is still present in the workspace
		assertTrue(checkFileExists(directoryPath + "/client.zip"));
		//assert that imported file was overwritten
		assertFalse(checkContentEquals(createTempFile("expectedFile", fileContents), directoryPath + "/client.zip"));
		assertTrue(checkContentEquals(source, directoryPath + "/client.zip"));
	}

	@Test
	public void testImportAndUnzipWithoutOverride() throws CoreException, IOException, SAXException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		String filePath = "/org.eclipse.e4.webide/static/js/navigate-tree";
		createDirectory(directoryPath + filePath);
		//create a file that is not overwritten
		String fileContents = "This is the file contents";
		createFile(directoryPath + filePath + "/navigate-tree.js", fileContents);
		//start the import
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/client.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		InputStream in = new BufferedInputStream(new FileInputStream(source));
		PostMethodWebRequest request = new PostMethodWebRequest(getImportRequestPath(directoryPath), in, "application/zip");
		request.setHeaderField("Content-Length", "" + length);
		request.setHeaderField("Content-Type", "application/octet-stream");
		request.setHeaderField("Slug", "client.zip");
		request.setHeaderField("X-Xfer-Options", "no-overwrite");
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		//assert the server rejects the override
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, postResponse.getResponseCode());
		//assert the unzip still occurred in the workspace
		assertTrue(checkFileExists(directoryPath + "/org.eclipse.e4.webide/static/images/unit_test/add-test-config.png"));
		//assert that imported file was not overwritten
		assertTrue(checkContentEquals(createTempFile("expectedFile", fileContents), directoryPath + filePath + "/navigate-tree.js"));
	}

	@Test
	public void testImportAndUnzipWithOverride() throws CoreException, IOException, SAXException {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		String filePath = "/org.eclipse.e4.webide/static/js/navigate-tree";
		createDirectory(directoryPath + filePath);
		//create a file that is to be overwritten
		String fileContents = "This is the file contents";
		createFile(directoryPath + filePath + "/navigate-tree.js", fileContents);
		//start the import
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/importTest/client.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		InputStream in = new BufferedInputStream(new FileInputStream(source));
		PostMethodWebRequest request = new PostMethodWebRequest(getImportRequestPath(directoryPath), in, "application/zip");
		request.setHeaderField("Content-Length", "" + length);
		request.setHeaderField("Content-Type", "application/octet-stream");
		request.setHeaderField("Slug", "client.zip");
		request.setHeaderField("X-Xfer-Options", "overwrite-older");
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		//assert the server accepts the override
		assertEquals(HttpURLConnection.HTTP_CREATED, postResponse.getResponseCode());
		String location = postResponse.getHeaderField("Location");
		assertNotNull(location);
		String type = postResponse.getHeaderField("Content-Type");
		assertNotNull(type);
		//assert the unzip still occurred in the workspace
		assertTrue(checkFileExists(directoryPath + "/org.eclipse.e4.webide/static/images/unit_test/add-test-config.png"));
		//assert that imported file was overwritten
		assertFalse(checkContentEquals(createTempFile("expectedFile", fileContents), directoryPath + filePath + "/navigate-tree.js"));
	}
}
