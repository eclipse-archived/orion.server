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
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitUtilsTest extends GitTest {
	@Test
	public void testGitDirPathNoGit() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(location);
		assertNull(GitUtils.getGitDir(new Path(uri.getPath())));
	}

	@Test
	public void testGitDirPathLinked() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), gitDir.toURI().toString());
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(location);
		File gitDirFile = GitUtils.getGitDir(new Path(uri.getPath()));
		assertNotNull(gitDirFile);
		assertEquals(gitDir, gitDirFile.getParentFile());
	}

	@Test
	public void testGitDirPathLinkedToSubfolder() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), new File(gitDir, "folder").toURI().toString());
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(location);
		Set<Entry<IPath, File>> set = GitUtils.getGitDirs(new Path(uri.getPath()), Traverse.GO_UP).entrySet();
		assertEquals(1, set.size());
		Entry<IPath, File> entry = set.iterator().next();
		File gitDirFile = entry.getValue();
		assertNotNull(gitDirFile);
		assertEquals(gitDir, gitDirFile.getParentFile());
		IPath path = entry.getKey();
		assertEquals(new Path("../"), path);
	}

	@Test
	public void testGitDirEmptyPath() throws Exception {
		assertNull(GitUtils.getGitDir(new Path("")));
	}

	@Test
	public void testGitDirsNoGit() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(location);
		IPath projectPath = new Path(uri.getPath());
		Map<IPath, File> gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
		assertTrue(gitDirs.isEmpty());
	}

	@Test
	public void testGitDirPathLinkedRemovedFile() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), gitDir.toURI().toString());
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// drill down to folder/folder.txt and delete it
		WebRequest request = getGetFilesRequest(location);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject jsonResponse = new JSONObject(response.getText());
		request = getGetFilesRequest(jsonResponse.getString(ProtocolConstants.KEY_CHILDREN_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));
		JSONObject testTxt = getChildByName(children, "folder");
		String folderLocation = testTxt.getString(ProtocolConstants.KEY_LOCATION);
		request = getGetFilesRequest(folderLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		jsonResponse = new JSONObject(response.getText());
		request = getGetFilesRequest(jsonResponse.getString(ProtocolConstants.KEY_CHILDREN_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		children = getDirectoryChildren(new JSONObject(response.getText()));
		JSONObject folderTxt = getChildByName(children, "folder.txt");
		String folderTxtLocation = folderTxt.getString(ProtocolConstants.KEY_LOCATION);
		request = getDeleteFilesRequest(folderTxtLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// then try to get git dir for the removed file
		URI uri = URI.create(folderTxtLocation);
		File gitDirFile = GitUtils.getGitDir(new Path(uri.getPath()));
		assertNotNull(gitDirFile);
		assertEquals(gitDir, gitDirFile.getParentFile());
	}

	@Test
	@Ignore(/*TODO*/"not yet implemented")
	public void testGitDirsLinked() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), gitDir.toURI().toString());
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(location);
		IPath projectPath = new Path(uri.getPath());
		Map<IPath, File> gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
		assertFalse(gitDirs.isEmpty());
	}

	@Test
	public void testGitDirsCloned() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).makeAbsolute();
		clone(clonePath);
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(location);
		IPath projectPath = new Path(uri.getPath()).removeFirstSegments(1); // remove /file
		Map<IPath, File> gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
		assertFalse(gitDirs.isEmpty());
	}

	@Test
	public void testGitDirsClonedIntoSubfolder() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);

		// create folder1
		String folderName = "clone1";
		WebRequest request = getPostFilesRequest(projectId + "/", getNewDirJSON(folderName).toString(), folderName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject cloneFolder1 = new JSONObject(response.getText());
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).append(folderName).makeAbsolute();
		clone(clonePath);

		// create folder2
		folderName = "clone2";
		request = getPostFilesRequest(projectId + "/", getNewDirJSON(folderName).toString(), folderName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject cloneFolder2 = new JSONObject(response.getText());
		clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).append(folderName).makeAbsolute();
		clone(clonePath);

		String cloneLocation = cloneFolder1.getString(ProtocolConstants.KEY_LOCATION);
		URI subfolderUri = URI.create(cloneLocation);
		IPath subfolderPath = new Path(subfolderUri.getPath()).removeFirstSegments(1); // remove /file
		Map<IPath, File> gitDirs = GitUtils.getGitDirs(subfolderPath, Traverse.GO_DOWN);
		assertFalse(gitDirs.isEmpty());
		gitDirs.clear();

		cloneLocation = cloneFolder2.getString(ProtocolConstants.KEY_LOCATION);
		subfolderUri = URI.create(cloneLocation);
		subfolderPath = new Path(subfolderUri.getPath()).removeFirstSegments(1); // remove /file
		gitDirs = GitUtils.getGitDirs(subfolderPath, Traverse.GO_DOWN);
		assertFalse(gitDirs.isEmpty());
		gitDirs.clear();

		String projectLocation = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI projectUri = URI.create(projectLocation);
		IPath projectPath = new Path(projectUri.getPath()).removeFirstSegments(1); // remove /file
		gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
		assertFalse(gitDirs.isEmpty());
		assertEquals(2, gitDirs.size());
	}

	@Test
	public void testGetRelativePath() throws Exception {
		assertEquals(Path.EMPTY.toString(), GitUtils.getRelativePath(new Path("/file").append("a/"), Path.EMPTY));
		assertEquals(Path.EMPTY.toString(), GitUtils.getRelativePath(new Path("/file").append("a").append("b/"), Path.EMPTY));
		assertEquals("b/", GitUtils.getRelativePath(new Path("/file").append("a").append("b/"), new Path("../")));
		assertEquals("b/c/", GitUtils.getRelativePath(new Path("/file").append("a").append("b").append("c/"), new Path("../../")));
		assertEquals("c/", GitUtils.getRelativePath(new Path("/file").append("a").append("b").append("c/"), new Path("../")));
		assertEquals("b/c", GitUtils.getRelativePath(new Path("/file").append("a").append("b").append("c"), new Path("../")));
	}
}
