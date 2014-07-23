package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;

import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitStashTest extends GitTest {

	@Test
	public void testStashCreateEmpty() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		WebResponse response = stashCreate(project, false);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

	}

	@Test
	public void testStashCreateIncludeUnstaged() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		project = getExtraProjectData(project);

		String filename1 = "test2.txt"; //$NON-NLS-1$
		String filename2 = "test3.txt"; //$NON-NLS-1$

		createFile(project, filename1);
		createFile(project, filename2);

		assertStatus(new StatusResult().setUntracked(2), gitStatusUri);

		WebResponse response = stashCreate(project, true);

		assertStatus(new StatusResult().setUntracked(0), gitStatusUri);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testStashCreateKeepUnstaged() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		project = getExtraProjectData(project);

		String filename1 = "test2.txt"; //$NON-NLS-1$
		String filename2 = "test3.txt"; //$NON-NLS-1$

		createFile(project, filename1);
		createFile(project, filename2);

		assertStatus(new StatusResult().setUntracked(2), gitStatusUri);

		WebResponse response = stashCreate(project, false);

		assertStatus(new StatusResult().setUntracked(2), gitStatusUri);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
	}

	@Test
	public void testStashApplyEmpty() throws Exception {
		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		project = getExtraProjectData(project);

		WebResponse response = stashApply(project, false, false);

		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
		assertEquals(GitConstants.STASH_ILLEGAL_REF_MESSAGE, new JSONObject(response.getText()).getString("Message")); //$NON-NLS-1$
	}

	@Test
	public void testStashApplyAll() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		project = getExtraProjectData(project);

		String untrackedFile = "untracked.txt"; //$NON-NLS-1$
		String indexedFile = "indexed.txt"; //$NON-NLS-1$
		String indexedFileModifiedContent = "some content"; //$NON-NLS-1$

		JSONObject indexedFileJSON = createFile(project, indexedFile);
		addFile(indexedFileJSON);
		createFile(project, untrackedFile);
		modifyFile(indexedFileJSON, indexedFileModifiedContent);

		assertStatus(new StatusResult().setUntracked(1).setAdded(1).setModified(1), gitStatusUri);

		stashCreate(project, true);

		assertStatus(new StatusResult().setUntracked(0).setAdded(0), gitStatusUri);

		WebResponse response = stashApply(project, true, true);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(new StatusResult().setUntracked(1).setAdded(1).setModified(1), gitStatusUri);

	}

	@Test
	public void testStashApply() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		project = getExtraProjectData(project);

		String untrackedFile = "untracked.txt"; //$NON-NLS-1$
		String indexedFile = "indexed.txt"; //$NON-NLS-1$

		JSONObject indexedFileJSON = createFile(project, indexedFile);
		addFile(indexedFileJSON);
		createFile(project, untrackedFile);

		assertStatus(new StatusResult().setUntracked(1).setAdded(1), gitStatusUri);

		stashCreate(project, true);

		assertStatus(new StatusResult().setUntracked(0).setAdded(0), gitStatusUri);

		WebResponse response = stashApply(project, false, false);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(new StatusResult().setUntracked(0).setAdded(1), gitStatusUri);

	}

	@Test
	public void testStashApplyUnstaged() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		project = getExtraProjectData(project);

		String untrackedFile = "untracked.txt"; //$NON-NLS-1$
		String indexedFile = "indexed.txt"; //$NON-NLS-1$

		JSONObject indexedFileJSON = createFile(project, indexedFile);
		addFile(indexedFileJSON);
		createFile(project, untrackedFile);

		assertStatus(new StatusResult().setUntracked(1).setAdded(1), gitStatusUri);

		stashCreate(project, true);

		assertStatus(new StatusResult().setUntracked(0).setAdded(0), gitStatusUri);

		WebResponse response = stashApply(project, false, true);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(new StatusResult().setUntracked(1).setAdded(1), gitStatusUri);

	}

	@Test
	public void testStashApplyIndex() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		project = getExtraProjectData(project);

		String untrackedFile = "untracked.txt"; //$NON-NLS-1$
		String indexedFile = "indexed.txt"; //$NON-NLS-1$

		JSONObject indexedFileJSON = createFile(project, indexedFile);
		addFile(indexedFileJSON);
		createFile(project, untrackedFile);
		addFile(indexedFileJSON);
		commitFile(indexedFileJSON, "message"); //$NON-NLS-1$
		modifyFile(indexedFileJSON, "some mod"); //$NON-NLS-1$

		assertStatus(new StatusResult().setUntracked(1).setModified(1), gitStatusUri);

		stashCreate(project, true);

		assertStatus(new StatusResult().setUntracked(0).setModified(0), gitStatusUri);

		WebResponse response = stashApply(project, true, false);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(new StatusResult().setUntracked(0).setModified(1), gitStatusUri);

	}

	@Test
	public void testStashIllegalOperationType() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		String location = project.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_STASH);
		location = toAbsoluteURI(location);

		WebRequest request = getStashPostRequest(location, "illegal"); //$NON-NLS-1$
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

		request = getStashPostRequest(location, null);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
		assertEquals(GitConstants.STASH_MISSING_OPERATION_TYPE, new JSONObject(response.getText()).getString("Message")); //$NON-NLS-1$

	}

	@Test
	public void testStashList() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		project = getExtraProjectData(project);

		String filename1 = "test2.txt"; //$NON-NLS-1$
		String filename2 = "test3.txt"; //$NON-NLS-1$

		createFile(project, filename1);
		stashCreate(project, true);
		createFile(project, filename2);
		stashCreate(project, true);

		WebResponse response = stashList(project);
		String resp = response.getText();
		JSONArray result = new JSONArray(resp);

		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(2, result.length());

	}

	@Test
	public void testStashListPaginated() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		project = getExtraProjectData(project);

		String[] filenames = new String[] { // 
		"file1.txt", "file2.txt", "file3.txt", "file4.txt", "file5.txt", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				"file6.txt", "file7.txt", "file8.txt", "file9.txt", "file10.txt", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				"file11.txt", "file12.txt"}; //$NON-NLS-1$ //$NON-NLS-2$

		for (String filename : filenames) {
			createFile(project, filename);
			stashCreate(project, true);
		}

		WebResponse response = stashList(project, 1, 10);
		String resp = response.getText();
		JSONArray result = new JSONArray(resp);

		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(10, result.length());

		response = stashList(project, 2, 10);
		resp = response.getText();
		result = new JSONArray(resp);

		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(2, result.length());

		response = stashList(project);
		resp = response.getText();
		result = new JSONArray(resp);

		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		assertEquals(10, result.length());

		response = stashList(project, -1, 10);

		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());

		response = stashList(project, 1, 0);

		assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.getResponseCode());
	}

	@Test
	public void testStashDrop() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		project = getExtraProjectData(project);

		String[] filenames = new String[] { // 
		"file1.txt", "file2.txt", "file3.txt"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		for (String filename : filenames) {
			createFile(project, filename);
			stashCreate(project, true);
		}

		WebResponse response = stashList(project);
		JSONArray result = new JSONArray(response.getText());

		assertEquals(3, result.length());

		stashDrop(project, 0);

		response = stashList(project);
		result = new JSONArray(response.getText());

		assertEquals(2, result.length());

		stashDrop(project, -1);

		response = stashList(project);
		result = new JSONArray(response.getText());

		assertEquals(0, result.length());

	}

	static WebRequest getStashCreatePostRequest(String location, boolean includeUntracked, String indexMessage, String workingDirectoryMessage) throws Exception {

		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_STASH_POST_OPERATION_TYPE, GitConstants.KEY_STASH_CREATE_COMMAND);
		body.put(GitConstants.KEY_STASH_WORKING_DIRECTORY_MESSAGE, workingDirectoryMessage);
		body.put(GitConstants.KEY_STASH_INDEX_MESSAGE, indexMessage);
		body.put(GitConstants.KEY_STASH_INCLUDE_UNTRACKED, Boolean.toString(includeUntracked));
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8"); //$NON-NLS-1$
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1"); //$NON-NLS-1$
		setAuthentication(request);
		return request;
	}

	static WebRequest getStashApplyPostRequest(String location, boolean applyIndex, boolean applyUntracked) throws Exception {

		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_STASH_POST_OPERATION_TYPE, GitConstants.KEY_STASH_APPLY_COMMAND);
		body.put(GitConstants.KEY_STASH_APPLY_INDEX, Boolean.toString(applyIndex));
		body.put(GitConstants.KEY_STASH_APPLY_UNTRACKED, Boolean.toString(applyUntracked));
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8"); //$NON-NLS-1$
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1"); //$NON-NLS-1$
		setAuthentication(request);
		return request;
	}

	static WebRequest getStashListGetRequest(String location) {
		String requestURI = toAbsoluteURI(location);
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1"); //$NON-NLS-1$
		setAuthentication(request);
		return request;
	}

	/**
	 * Generic POST request to test for invalid operation types
	 * @param location
	 * @return
	 */
	static WebRequest getStashPostRequest(String location, String opType) throws Exception {
		String requestURI = toAbsoluteURI(location);
		WebRequest request = new PostMethodWebRequest(requestURI);
		JSONObject body = new JSONObject();

		if (opType != null) {
			body.put("operationType", opType); //$NON-NLS-1$
		}

		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1"); //$NON-NLS-1$
		setAuthentication(request);
		return request;
	}

	/**
	 * 
	 * @param location
	 * @param stashRef non-negative integer or -1 to drop all references
	 * @return
	 * @throws Exception
	 */
	static WebRequest getStashDeleteRequest(String location, int stashRef) throws Exception {

		String requestURI = toAbsoluteURI(location);
		WebRequest request = new DeleteMethodWebRequest(requestURI);

		request.setParameter(GitConstants.KEY_STASH_DROP_REF, Integer.toString(stashRef));

		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1"); //$NON-NLS-1$
		setAuthentication(request);
		return request;
	}

	protected WebResponse stashCreate(JSONObject project, boolean includeUntracked) throws Exception {
		return stashCreate(project, includeUntracked, null, null);
	}

	private WebResponse stashCreate(JSONObject project, boolean includeUntracked, String indexMessage, String workingDirectoryMessage) throws Exception {

		String location = project.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_STASH);

		WebRequest request = GitStashTest.getStashCreatePostRequest(location, includeUntracked, indexMessage, workingDirectoryMessage);
		WebResponse response = webConversation.getResponse(request);

		return response;
	}

	private WebResponse stashApply(JSONObject project, boolean applyIndex, boolean applyUntracked) throws Exception {

		String location = project.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_STASH);

		WebRequest request = GitStashTest.getStashApplyPostRequest(location, applyIndex, applyUntracked);
		WebResponse response = webConversation.getResponse(request);

		return response;
	}

	private WebResponse stashList(JSONObject project, int page, int pageSize) throws Exception {

		String location = project.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_STASH);

		WebRequest request = GitStashTest.getStashListGetRequest(location);

		request.setParameter(GitConstants.KEY_STASH_LIST_PAGE, Integer.toString(page));
		request.setParameter(GitConstants.KEY_STASH_LIST_PAGE_SIZE, Integer.toString(pageSize));

		WebResponse response = webConversation.getResponse(request);

		return response;

	}

	private WebResponse stashList(JSONObject project) throws Exception {

		String location = project.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_STASH);
		WebRequest request = GitStashTest.getStashListGetRequest(location);
		WebResponse response = webConversation.getResponse(request);

		return response;
	}

	private WebResponse stashDrop(JSONObject project, int stashRef) throws Exception {
		String location = project.getJSONObject(GitConstants.KEY_GIT).getString(GitConstants.KEY_STASH);

		WebRequest request = GitStashTest.getStashDeleteRequest(location, stashRef);
		WebResponse response = webConversation.getResponse(request);

		return response;
	}
}
