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
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.ProtocolConstants;
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
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(location);
		File dir = GitUtils.getGitDir(new Path(uri.getPath()));
		assertNull(dir == null ? "N/A" : dir.toURI().toURL().toString(), dir);
	}

	@Test
	public void testGitDirPathLinked() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), gitDir.toURI().toString());
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(toRelativeURI(location));
		File gitDirFile = GitUtils.getGitDir(new Path(uri.getPath()));
		assertNotNull(gitDirFile);
		assertEquals(gitDir, gitDirFile.getParentFile());
	}

	@Test
	public void testGitDirPathLinkedToSubfolder() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), new File(gitDir, "folder").toURI().toString());
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(toRelativeURI(location));
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
		File emptyPathFile = GitUtils.getGitDir(new Path(""));
		assertNull(emptyPathFile == null ? "N/A" : emptyPathFile.toURI().toURL().toString(), emptyPathFile);
	}

	private boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}

		// The directory is now empty so delete it
		return dir.delete();
	}

	@Test
	public void testGetGitDirWorkspaceIsInRepo() throws Exception {
		InitCommand command = new InitCommand();
		File workspace = getWorkspaceRoot();
		File parent = workspace.getParentFile();
		command.setDirectory(parent);
		Repository repository = command.call().getRepository();
		assertNotNull(repository);

		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(location);
		File dir = GitUtils.getGitDir(new Path(uri.getPath()));
		assertNull(dir == null ? "N/A" : dir.toURI().toURL().toString(), dir);

		File[] parentChildren = parent.listFiles();
		for (int i = 0; i < parentChildren.length; i++) {
			if (parentChildren[i].getName().equals(".git")) {
				assertTrue(deleteDir(parentChildren[i]));
			}
		}
	}

	@Test
	public void testGitDirsNoGit() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(toRelativeURI(location));
		IPath projectPath = new Path(uri.getPath());
		Map<IPath, File> gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
		assertTrue(gitDirs.isEmpty());
	}

	@Test
	public void testGitDirPathLinkedRemovedFile() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), gitDir.toURI().toString());
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

		// drill down to folder/folder.txt and delete it
		WebRequest request = getGetRequest(location);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject jsonResponse = new JSONObject(response.getText());
		request = getGetRequest(jsonResponse.getString(ProtocolConstants.KEY_CHILDREN_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		List<JSONObject> children = getDirectoryChildren(new JSONObject(response.getText()));
		JSONObject testTxt = getChildByName(children, "folder");
		String folderLocation = testTxt.getString(ProtocolConstants.KEY_LOCATION);
		request = getGetRequest(folderLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		jsonResponse = new JSONObject(response.getText());
		request = getGetRequest(jsonResponse.getString(ProtocolConstants.KEY_CHILDREN_LOCATION));
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		children = getDirectoryChildren(new JSONObject(response.getText()));
		JSONObject folderTxt = getChildByName(children, "folder.txt");
		String folderTxtLocation = folderTxt.getString(ProtocolConstants.KEY_LOCATION);
		request = getDeleteFilesRequest(folderTxtLocation);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// then try to get git directory for the removed file
		URI uri = URI.create(toRelativeURI(folderTxtLocation));
		File gitDirFile = GitUtils.getGitDir(new Path(uri.getPath()));
		assertNotNull(gitDirFile);
		assertEquals(gitDir, gitDirFile.getParentFile());
	}

	@Test
	@Ignore(/*TODO*/"not yet implemented")
	public void testGitDirsLinked() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), gitDir.toURI().toString());
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(location);
		IPath projectPath = new Path(uri.getPath());
		Map<IPath, File> gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
		assertFalse(gitDirs.isEmpty());
	}

	@Test
	public void testGitDirsCloned() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);
		IPath clonePath = getClonePath(workspaceId, project);
		clone(clonePath);
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(toRelativeURI(location));
		IPath projectPath = new Path(uri.getPath());
		Map<IPath, File> gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
		assertFalse(gitDirs.isEmpty());
	}

	@Test
	public void testGitDirsClonedIntoSubfolder() throws Exception {
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		String workspaceId = workspaceIdFromLocation(workspaceLocation);
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName().concat("Project"), null);

		// create folder1
		String folderName = "clone1";
		WebRequest request = getPostFilesRequest("", getNewDirJSON(folderName).toString(), folderName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject cloneFolder1 = new JSONObject(response.getText());
		IPath clonePath = getClonePath(workspaceId, project).append(folderName).makeAbsolute();
		clone(clonePath);

		// create folder2
		folderName = "clone2";
		request = getPostFilesRequest("", getNewDirJSON(folderName).toString(), folderName);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject cloneFolder2 = new JSONObject(response.getText());
		clonePath = getClonePath(workspaceId, project).append(folderName).makeAbsolute();
		clone(clonePath);

		String cloneLocation = cloneFolder1.getString(ProtocolConstants.KEY_LOCATION);
		URI subfolderUri = URI.create(toRelativeURI(cloneLocation));
		IPath subfolderPath = new Path(subfolderUri.getPath());
		Map<IPath, File> gitDirs = GitUtils.getGitDirs(subfolderPath, Traverse.GO_DOWN);
		assertFalse(gitDirs.isEmpty());
		gitDirs.clear();

		cloneLocation = cloneFolder2.getString(ProtocolConstants.KEY_LOCATION);
		subfolderUri = URI.create(toRelativeURI(cloneLocation));
		subfolderPath = new Path(subfolderUri.getPath());
		gitDirs = GitUtils.getGitDirs(subfolderPath, Traverse.GO_DOWN);
		assertFalse(gitDirs.isEmpty());
		gitDirs.clear();

		String projectLocation = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI projectUri = URI.create(toRelativeURI(projectLocation));
		IPath projectPath = new Path(projectUri.getPath());
		gitDirs = GitUtils.getGitDirs(projectPath, Traverse.GO_DOWN);
		assertFalse(gitDirs.isEmpty());
		assertEquals(2, gitDirs.size());
	}

	@Test
	public void testGetRelativePath() throws Exception {
		assertEquals(Path.EMPTY.toString(), GitUtils.getRelativePath(new Path("/file/a/"), Path.EMPTY));
		assertEquals(Path.EMPTY.toString(), GitUtils.getRelativePath(new Path("/file/a/b/"), Path.EMPTY));
		assertEquals("b/", GitUtils.getRelativePath(new Path("/file/a/b/"), new Path("../")));
		assertEquals("b/c/", GitUtils.getRelativePath(new Path("/file/a/b/c/"), new Path("../../")));
		assertEquals("c/", GitUtils.getRelativePath(new Path("/file/a/b/c/"), new Path("../")));
		assertEquals("b/c", GitUtils.getRelativePath(new Path("/file/a/b/c"), new Path("../")));
	}
}
