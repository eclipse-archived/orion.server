package org.eclipse.orion.server.tests.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.search.SearchActivator;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.eclipse.orion.server.tests.servlets.xfer.TransferTest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.HttpUnitOptions;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GrepSearchTest extends FileSystemTest {

	private static final String SEARCH_LOCATION = toAbsoluteURI("grepsearch");

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	/**
	 * Setup a project with some files that we can use for search tests.
	 * @throws CoreException 
	 * @throws IOException 
	 * @throws SAXException 
	 */
	private void createTestData() throws Exception {
		//create a directory to upload to
		String directoryPath = "sample/directory/path" + System.currentTimeMillis();
		createDirectory(directoryPath);

		//start the import
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/searchTest/files.zip");
		File source = new File(FileLocator.toFileURL(entry).getPath());
		long length = source.length();
		IPath path = new Path("/xfer/import").append(getTestBaseResourceURILocation()).append(directoryPath);
		PostMethodWebRequest request = new PostMethodWebRequest(URIUtil.fromString(SERVER_LOCATION + path.toString()).toString());
		request.setHeaderField("X-Xfer-Content-Length", Long.toString(length));
		setAuthentication(request);
		WebResponse postResponse = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, postResponse.getResponseCode());
		String location = postResponse.getHeaderField("Location");
		assertNotNull(location);

		doImport(source, length, location);
	}

	/**
	 * Copied from {@link TransferTest}.
	 */
	private void doImport(File source, long length, String location) throws FileNotFoundException, IOException, SAXException {
		//repeat putting chunks until done
		byte[] chunk = new byte[64 * 1024];
		InputStream in = new BufferedInputStream(new FileInputStream(source));
		int chunkSize = 0;
		int totalTransferred = 0;
		while ((chunkSize = in.read(chunk, 0, chunk.length)) > 0) {
			PutMethodWebRequest put = new PutMethodWebRequest(toAbsoluteURI(location), new ByteArrayInputStream(chunk, 0, chunkSize), "application/zip");
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
		in.close();
	}

	private JSONObject doSearch(String query, int expectedResponseCode) throws Exception {
		WebRequest request = new GetMethodWebRequest(SEARCH_LOCATION + query);
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(expectedResponseCode, response.getResponseCode());
		return new JSONObject(response.getText());

	}

	private JSONObject doSearch(String query) throws Exception {
		return doSearch(query, HttpURLConnection.HTTP_OK);
	}

	@Before
	public void setUp() throws Exception {
		HttpUnitOptions.setDefaultCharacterSet("UTF-8");
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		createTestProject(testName.getMethodName());
		createTestData();
		//wait for indexer to finish
		SearchActivator.getInstance().testWaitForIndex();
	}

	/**
	 * Tests finding search results on a part of a word.
	 */
	@Test
	public void testPartialWord() throws Exception {
		JSONObject searchResult = doSearch("?q=ErrorMess");
		JSONArray arr = searchResult.getJSONArray("docs");
		assertEquals(1, arr.length());
		assertTrue(arr.getJSONObject(0).getString("Name").endsWith("script.js"));
	}

}
