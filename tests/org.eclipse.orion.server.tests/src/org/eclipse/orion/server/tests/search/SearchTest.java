/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.meterware.httpunit.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.search.SearchActivator;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.eclipse.orion.server.tests.servlets.xfer.TransferTest;
import org.json.*;
import org.junit.*;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * Tests for the search servlet.
 */
public class SearchTest extends FileSystemTest {

	private static final String SEARCH_LOCATION = ServerTestsActivator.getServerLocation() + "/filesearch?q=";
	private String oldTestUserLogin;

	@BeforeClass
	public static void setupWorkspace() {
		initializeWorkspaceLocation();
	}

	/**
	 * Asserts that a search result contains no matches.
	 */
	private void assertNoMatch(JSONObject searchResult) throws JSONException {
		JSONObject response = searchResult.getJSONObject("response");
		assertEquals(0, response.getInt("numFound"));
	}

	/**
	 * Asserts that a search result has exactly one match.
	 */
	private void assertOneMatch(JSONObject searchResult, String file) throws JSONException {
		JSONObject response = searchResult.getJSONObject("response");
		assertEquals(1, response.getInt("numFound"));
		JSONArray docs = response.getJSONArray("docs");
		JSONObject single = docs.getJSONObject(0);
		assertEquals(file, single.getString("Name"));
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
		String importPath = ServerTestsActivator.getServerLocation() + path.toString();
		PostMethodWebRequest request = new PostMethodWebRequest(importPath);
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

	private JSONObject doSearch(String query) throws Exception {
		WebRequest request = new GetMethodWebRequest(SEARCH_LOCATION + query);
		setAuthentication(request);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		return new JSONObject(response.getText());
	}

	@Before
	public void setUp() throws Exception {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		//use a different user to avoid search result contamination from other tests
		oldTestUserLogin = testUserLogin;
		testUserLogin = "SearchTestUser";
		setUpAuthorization();
		//search tests don't damage files, so only need to do this once per suite
		if (!testProjectExists("SearchTestProject")) {
			createTestProject("SearchTest");
			createTestData();
			//wait for indexer to finish
			SearchActivator.getInstance().testWaitForIndex();
		}
	}

	@After
	public void tearDown() {
		//reset back to default user login
		testUserLogin = oldTestUserLogin;
	}

	/**
	 * Returns whether a project already exists with the given name.
	 */
	private boolean testProjectExists(String name) {
		for (WebProject project : WebProject.allProjects()) {
			if (name.equals(project.getName()))
				return true;
		}
		return false;
	}

	/**
	 * Tests finding search results on a part of a word.
	 */
	@Test
	public void testPartialWord() throws Exception {
		JSONObject searchResult = doSearch("ErrorMess");
		assertOneMatch(searchResult, "script.js");

	}

	/**
	 * Tests searching for a sequence of words in a particular order
	 * @throws Exception
	 */
	@Test
	public void testPhraseSearch() throws Exception {
		//these same words appear in a.txt in a different order
		JSONObject searchResult = doSearch("%22Eclipse Public License%22");
		assertOneMatch(searchResult, "script.js");
	}

	/**
	 * Tests finding search results on a whole single word.
	 */
	@Test
	public void testSingleWord() throws Exception {
		//simple word in html tag
		JSONObject searchResult = doSearch("monkey");
		assertOneMatch(searchResult, "page.html");

		//word in quotes
		searchResult = doSearch("mammoth");
		assertOneMatch(searchResult, "a.txt");

		//compound word
		searchResult = doSearch("Will-o'-the-wisp");
		assertOneMatch(searchResult, "a.txt");

		//should not match
		searchResult = doSearch("Will-o'-the-whisk");
		assertNoMatch(searchResult);
	}

}
