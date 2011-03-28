package org.eclipse.orion.server.tests.servlets.site;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.site.SiteConfigurationConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Basic tests:
 * - Start, stop a site
 * - Access workspace files through paths on the running site
 * - Access a remote URL through paths on the running site
 * - Test starting site via PUT and POST (start on creation)
 * 
 * Security tests:
 * - Try to walk the workspace using ../ in hosted site path (should 404)
 * 
 * Concurrency tests (done concurrently on many threads)
 * - Create a file with unique name 
 * - Create a new site that exposes the file
 * - Start the site
 * - Access the site, verify file content.
 * - Stop the site
 * - Attempt to access the file again, verify that request 404s (or times out?)
 */
public class HostingTest extends CoreSiteTest {

	private WebResponse workspaceResponse;
	private JSONObject workspaceObject;

	@BeforeClass
	public static void setUpWorkspace() {
		initializeWorkspaceLocation();
	}

	@Before
	/**
	 * Before each test, create a workspace and prepare fields for use by test methods.
	 */
	public void setUp() throws CoreException, SAXException, IOException, JSONException {
		clearWorkspace();
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
		workspaceResponse = createWorkspace(this.getClass().getName());
		workspaceObject = new JSONObject(workspaceResponse.getText());
	}

	@Test
	/**
	 * Test starting, accessing a workspace file and remote URL, and stopping.
	 */
	public void testStartStop() throws SAXException, IOException, JSONException {
		// Create file in workspace
		final String fileName = "foo.html";
		final String fileContent = "<html><body>This is a test file</body></html>";
		webConversation.setExceptionsThrownOnErrorStatus(false);
		WebRequest createFileReq = getPostFilesRequest("/", getNewFileJSON(fileName).toString(), fileName);
		WebResponse createFileResp = webConversation.getResponse(createFileReq);
		assertEquals(HttpURLConnection.HTTP_CREATED, createFileResp.getResponseCode());
		createFileReq = getPutFileRequest(createFileResp.getHeaderField("Location"), fileContent);
		createFileResp = webConversation.getResponse(createFileReq);
		assertEquals(HttpURLConnection.HTTP_OK, createFileResp.getResponseCode());

		// Create a site that exposes the workspace file, and also a remote URL
		final String siteName = "My hosted site";
		final String workspaceId = workspaceObject.getString(ProtocolConstants.KEY_ID);
		final String filePath = "/" + fileName;
		final String mountAt1 = "/file.html";
		final String mountAt2 = "/web";
		// Not really "remote", but will suffice
		final String remoteURL = SERVER_LOCATION + "/navigate-table.html";
		final JSONArray mappings = makeMappings(new String[][] { {mountAt1, filePath}, {mountAt2, remoteURL}});
		WebRequest createSiteReq = getCreateSiteRequest(siteName, workspaceId, mappings, null);
		WebResponse createSiteResp = webConversation.getResponse(createSiteReq);
		assertEquals(HttpURLConnection.HTTP_CREATED, createSiteResp.getResponseCode());
		JSONObject siteObject = new JSONObject(createSiteResp.getText());

		// Start the site
		final String siteLocation = siteObject.getString(ProtocolConstants.KEY_LOCATION);//createSiteResp.getHeaderField("Location");
		JSONObject hostingStatus = new JSONObject();
		hostingStatus.put(SiteConfigurationConstants.KEY_HOSTING_STATUS_STATUS, "started");
		WebRequest launchSiteReq = getUpdateSiteRequest(siteLocation, null, null, null, null, hostingStatus);
		WebResponse launchSiteResp = webConversation.getResponse(launchSiteReq);
		assertEquals(HttpURLConnection.HTTP_OK, launchSiteResp.getResponseCode());
		siteObject = new JSONObject(launchSiteResp.getText());

		// Check that it's started
		hostingStatus = siteObject.getJSONObject(SiteConfigurationConstants.KEY_HOSTING_STATUS);
		assertEquals("started", hostingStatus.getString(SiteConfigurationConstants.KEY_HOSTING_STATUS_STATUS));

		// Access the workspace file through the site
		final String hostedURL = hostingStatus.getString(SiteConfigurationConstants.KEY_HOSTING_STATUS_URL);
		WebRequest getFileReq = new GetMethodWebRequest(hostedURL + mountAt1);
		WebResponse getFileResp = webConversation.getResponse(getFileReq);
		assertEquals(fileContent, getFileResp.getText());

		// Access the remote URL through the site
		WebRequest getRemoteUrlReq = new GetMethodWebRequest(hostedURL + mountAt2);
		// just fetch content, don't try to parse
		WebResponse getRemoteUrlResp = webConversation.getResource(getRemoteUrlReq);
		assertEquals(HttpURLConnection.HTTP_OK, getRemoteUrlResp.getResponseCode());
		final String content = getRemoteUrlResp.getText();
		assertTrue("Looks like Orion nav page", content.contains("Orion") && content.contains(".js") && content.contains("<script") && content.toLowerCase().contains("navigator"));

		// Stop the site
		hostingStatus = new JSONObject();
		hostingStatus.put(SiteConfigurationConstants.KEY_HOSTING_STATUS_STATUS, "stopped");
		WebRequest stopReq = getUpdateSiteRequest(siteLocation, null, null, null, null, hostingStatus);
		WebResponse stopResp = webConversation.getResponse(stopReq);
		assertEquals(HttpURLConnection.HTTP_OK, stopResp.getResponseCode());
		siteObject = new JSONObject(stopResp.getText());
		assertEquals("stopped", hostingStatus.getString(SiteConfigurationConstants.KEY_HOSTING_STATUS_STATUS));

		// Check that the workspace file can't be accessed anymore
		WebRequest getFile404Req = new GetMethodWebRequest(hostedURL + mountAt1);
		WebResponse getFile404Resp = webConversation.getResponse(getFile404Req);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, getFile404Resp.getResponseCode());

		// Check that remote URL can't be accessed anymore
		WebRequest getRemoteUrl404Req = new GetMethodWebRequest(hostedURL + mountAt2);
		WebResponse getRemoteUrl404Resp = webConversation.getResponse(getRemoteUrl404Req);
		assertEquals(HttpURLConnection.HTTP_NOT_FOUND, getRemoteUrl404Resp.getResponseCode());
	}

}
