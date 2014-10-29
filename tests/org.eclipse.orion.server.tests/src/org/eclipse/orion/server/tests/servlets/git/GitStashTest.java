/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.tests.servlets.internal.DeleteMethodWebRequest;
import org.json.JSONObject;
import org.junit.Test;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.PutMethodWebRequest;
import com.meterware.httpunit.WebRequest;

public class GitStashTest extends GitTest {

	@Test
	public void testEmptyStashList() throws Exception {
		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		String stashLocation = getStashLocation(project);
		ServerStatus status = getStashList(stashLocation);
		assertTrue(status.isOK());

		JSONObject stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals(0, stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());
	}

	@Test
	public void testStashListPagination() throws Exception {
		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());
		String stashLocation = getStashLocation(project);

		int CHANGES = 12;
		int pageSize = 5;
		int k = CHANGES;

		while (k-- > 0) {
			JSONObject testTxt = getChild(project, "test.txt"); //$NON-NLS-1$
			modifyFile(testTxt, "change to stash " + String.valueOf(k)); //$NON-NLS-1$

			ServerStatus status = createStash(stashLocation);
			assertTrue(status.isOK());
		}

		ServerStatus status = getStashList(stashLocation, 1, pageSize);
		assertTrue(status.isOK());

		/* first page */
		JSONObject stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals(pageSize, stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());
		assertTrue(stash.has(ProtocolConstants.KEY_NEXT_LOCATION));
		assertFalse(stash.has(ProtocolConstants.KEY_PREVIOUS_LOCATION));

		status = getStashList(stash.getString(ProtocolConstants.KEY_NEXT_LOCATION));
		assertTrue(status.isOK());

		/* second page */
		stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals(pageSize, stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());
		assertTrue(stash.has(ProtocolConstants.KEY_NEXT_LOCATION));
		assertTrue(stash.has(ProtocolConstants.KEY_PREVIOUS_LOCATION));

		status = getStashList(stash.getString(ProtocolConstants.KEY_NEXT_LOCATION));
		assertTrue(status.isOK());

		/* third page */
		stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals((CHANGES - 2 * pageSize), stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());
		assertFalse(stash.has(ProtocolConstants.KEY_NEXT_LOCATION));
		assertTrue(stash.has(ProtocolConstants.KEY_PREVIOUS_LOCATION));
	}

	@Test
	public void testStashCreateWithUntracked() throws Exception {
		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt"); //$NON-NLS-1$
		modifyFile(testTxt, "change to stash"); //$NON-NLS-1$

		String stashLocation = getStashLocation(project);
		ServerStatus status = getStashList(stashLocation);
		assertTrue(status.isOK());

		JSONObject stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals(0, stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());

		status = createStash(stashLocation);
		assertTrue(status.isOK());

		status = getStashList(stashLocation);
		assertTrue(status.isOK());

		stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals(1, stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());
	}

	@Test
	public void testStashCreateWithUntrackedAndIndex() throws Exception {
		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt"); //$NON-NLS-1$
		modifyFile(testTxt, "change to stash"); //$NON-NLS-1$

		/* stage folder.txt */
		JSONObject folder1 = getChild(project, "folder"); //$NON-NLS-1$
		JSONObject folderTxt = getChild(folder1, "folder.txt"); //$NON-NLS-1$
		modifyFile(folderTxt, "change to stash"); //$NON-NLS-1$
		addFile(folderTxt);

		String stashLocation = getStashLocation(project);
		ServerStatus status = getStashList(stashLocation);
		assertTrue(status.isOK());

		JSONObject stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals(0, stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());

		status = createStash(stashLocation);
		assertTrue(status.isOK());

		status = getStashList(stashLocation);
		assertTrue(status.isOK());

		stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals(1, stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());

		JSONObject stashChange = stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0);
		assertTrue(stashChange.has(GitConstants.KEY_COMMIT_DIFFS));
		assertEquals(2, ((JSONObject) stashChange.get(GitConstants.KEY_COMMIT_DIFFS)).get("Length"));
	}

	@Test
	public void testStashApply() throws Exception {
		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt"); //$NON-NLS-1$
		modifyFile(testTxt, "change to stash"); //$NON-NLS-1$
		String beforeStash = getFileContent(testTxt);

		String stashLocation = getStashLocation(project);
		ServerStatus status = createStash(stashLocation);
		assertTrue(status.isOK());

		String afterStash = getFileContent(testTxt);
		assertFalse(beforeStash.equals(afterStash));

		status = getStashList(stashLocation);
		assertTrue(status.isOK());

		JSONObject stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals(1, stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());

		JSONObject change = stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0);
		assertTrue(change.has(GitConstants.KEY_STASH_APPLY_LOCATION));

		status = applyStash(change.getString(GitConstants.KEY_STASH_APPLY_LOCATION));
		assertTrue(status.isOK());

		status = getStashList(stashLocation);
		assertTrue(status.isOK());

		stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals(1, stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());

		String afterApply = getFileContent(testTxt);
		assertTrue(beforeStash.equals(afterApply));
	}

	@Test
	public void testStashDrop() throws Exception {
		String projectName = getMethodName();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, projectName, gitDir.toString());

		JSONObject testTxt = getChild(project, "test.txt"); //$NON-NLS-1$
		modifyFile(testTxt, "change to stash"); //$NON-NLS-1$

		String stashLocation = getStashLocation(project);
		ServerStatus status = createStash(stashLocation);
		assertTrue(status.isOK());

		status = getStashList(stashLocation);
		assertTrue(status.isOK());

		JSONObject stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals(1, stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());

		JSONObject change = stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).getJSONObject(0);
		assertTrue(change.has(GitConstants.KEY_STASH_DROP_LOCATION));

		status = dropStash(change.getString(GitConstants.KEY_STASH_DROP_LOCATION));
		assertTrue(status.isOK());

		status = getStashList(stashLocation);
		assertTrue(status.isOK());

		stash = status.getJsonData();
		assertTrue(stash.has(ProtocolConstants.KEY_CHILDREN));
		assertEquals(0, stash.getJSONArray(ProtocolConstants.KEY_CHILDREN).length());
	}

	protected String getStashLocation(JSONObject project) throws Exception {
		JSONObject clone = getCloneForGitResource(project);
		assertTrue(clone.has(GitConstants.KEY_STASH));
		return clone.getString(GitConstants.KEY_STASH);
	}

	protected ServerStatus getStashList(String stashLocation) throws Exception {
		WebRequest request = new GetMethodWebRequest(toAbsoluteURI(stashLocation));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1"); //$NON-NLS-1$
		setAuthentication(request);
		return waitForTask(webConversation.getResponse(request));
	}

	protected ServerStatus getStashList(String stashLocation, int page, int pageSize) throws Exception {
		WebRequest request = new GetMethodWebRequest(toAbsoluteURI(stashLocation));
		request.setParameter("page", String.valueOf(page)); //$NON-NLS-1$
		request.setParameter("pageSize", String.valueOf(pageSize)); //$NON-NLS-1$

		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1"); //$NON-NLS-1$
		setAuthentication(request);
		return waitForTask(webConversation.getResponse(request));
	}

	protected ServerStatus createStash(String stashLocation) throws Exception {
		WebRequest request = new PostMethodWebRequest(toAbsoluteURI(stashLocation));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1"); //$NON-NLS-1$
		setAuthentication(request);
		return waitForTask(webConversation.getResponse(request));
	}

	protected ServerStatus dropStash(String dropStashLocation) throws Exception {
		WebRequest request = new DeleteMethodWebRequest(toAbsoluteURI(dropStashLocation));
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1"); //$NON-NLS-1$
		setAuthentication(request);
		return waitForTask(webConversation.getResponse(request));
	}

	protected ServerStatus applyStash(String applyStashLocation) throws Exception {
		JSONObject body = new JSONObject();
		WebRequest request = new PutMethodWebRequest(toAbsoluteURI(applyStashLocation), IOUtilities.toInputStream(body.toString()), "application/json"); //$NON-NLS-1$

		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1"); //$NON-NLS-1$
		setAuthentication(request);
		return waitForTask(webConversation.getResponse(request));
	}
}
