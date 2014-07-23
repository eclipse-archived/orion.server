package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;

import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
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
		project = getContentLocationFromProject(project);

		String filename1 = "test2.txt";
		String filename2 = "test3.txt";

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
		project = getContentLocationFromProject(project);

		String filename1 = "test2.txt";
		String filename2 = "test3.txt";

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
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		project = getContentLocationFromProject(project);

		WebResponse response = stashApply(project, false, false);

		assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, response.getResponseCode());
		assertEquals(GitConstants.STASH_LIST_EMPTY_MESSAGE, new JSONObject(response.getText()).getString("Message"));
	}

	@Test
	public void testStashApplyAll() throws Exception {

		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		JSONObject gitSection = project.getJSONObject(GitConstants.KEY_GIT);
		String gitStatusUri = gitSection.getString(GitConstants.KEY_STATUS);
		project = getContentLocationFromProject(project);

		String untrackedFile = "untracked.txt";
		String indexedFile = "indexed.txt";
		String indexedFileModifiedContent = "some content";

		JSONObject indexedFileJSON = createFile(project, indexedFile);
		addFile(indexedFileJSON);
		JSONObject untrackedFileJSON = createFile(project, untrackedFile);
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
		project = getContentLocationFromProject(project);

		String untrackedFile = "untracked.txt";
		String indexedFile = "indexed.txt";

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
		project = getContentLocationFromProject(project);

		String untrackedFile = "untracked.txt";
		String indexedFile = "indexed.txt";

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
		project = getContentLocationFromProject(project);

		String untrackedFile = "untracked.txt";
		String indexedFile = "indexed.txt";

		JSONObject indexedFileJSON = createFile(project, indexedFile);
		addFile(indexedFileJSON);
		createFile(project, untrackedFile);
		addFile(indexedFileJSON);
		commitFile(indexedFileJSON, "message");
		modifyFile(indexedFileJSON, "some mod");

		assertStatus(new StatusResult().setUntracked(1).setModified(1), gitStatusUri);

		stashCreate(project, true);

		assertStatus(new StatusResult().setUntracked(0).setModified(0), gitStatusUri);

		WebResponse response = stashApply(project, true, false);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		assertStatus(new StatusResult().setUntracked(0).setModified(1), gitStatusUri);

	}

	static WebRequest getStashCreatePostRequest(String location, boolean includeUntracked, String indexMessage, String workingDirectoryMessage) throws Exception {

		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_STASH_POST_OPERATION_TYPE, GitConstants.KEY_STASH_CREATE_COMMAND);
		body.put(GitConstants.KEY_STASH_WORKING_DIRECTORY_MESSAGE, workingDirectoryMessage);
		body.put(GitConstants.KEY_STASH_INDEX_MESSAGE, indexMessage);
		body.put(GitConstants.KEY_STASH_INCLUDE_UNTRACKED, Boolean.toString(includeUntracked));
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getStashApplyPostRequest(String location, boolean applyIndex, boolean applyUntracked) throws Exception {

		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_STASH_POST_OPERATION_TYPE, GitConstants.KEY_STASH_APPLY_COMMAND);
		body.put(GitConstants.KEY_STASH_APPLY_INDEX, Boolean.toString(applyIndex));
		body.put(GitConstants.KEY_STASH_APPLY_UNTRACKED, Boolean.toString(applyUntracked));
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getStashListGetRequest(String location) {
		String requestURI = toAbsoluteURI(location);
		WebRequest request = new GetMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

	static WebRequest getStashDeleteRequest(String location, int stashRef, boolean dropAll) throws Exception {
		String requestURI = toAbsoluteURI(location);
		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_STASH_DROP_ALL, Boolean.toString(dropAll));
		body.put(GitConstants.KEY_STASH_DROP_REF, Integer.toString(stashRef));
		WebRequest request = new DeleteMethodWebRequest(requestURI);
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}

}
