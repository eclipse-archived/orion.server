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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.servlets.GitUtils;
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
		File gitDirFile = GitUtils.getGitDir(new Path(uri.getPath()));
		assertNotNull(gitDirFile);
		assertEquals(gitDir, gitDirFile.getParentFile());
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
		Map<IPath, File> gitDirs = new HashMap<IPath, File>();
		GitUtils.getGitDirs(projectPath, gitDirs);
		assertTrue(gitDirs.isEmpty());
	}

	@Test
	@Ignore(/*TODO*/"not yet implemented")
	public void testGitDirsLinked() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), gitDir.toURI().toString());
		String location = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI uri = URI.create(location);
		IPath projectPath = new Path(uri.getPath());
		Map<IPath, File> gitDirs = new HashMap<IPath, File>();
		GitUtils.getGitDirs(projectPath, gitDirs);
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
		Map<IPath, File> gitDirs = new HashMap<IPath, File>();
		GitUtils.getGitDirs(projectPath, gitDirs);
		assertFalse(gitDirs.isEmpty());
	}

	@Test
	public void testGitDirsClonedIntoSubfolder() throws Exception {
		URI workspaceLocation = createWorkspace(getMethodName());
		JSONObject project = createProjectOrLink(workspaceLocation, getMethodName(), null);
		String projectId = project.getString(ProtocolConstants.KEY_ID);
		String fileName = "subfolder";
		WebRequest request = getPostFilesRequest(projectId + "/", getNewDirJSON(fileName).toString(), fileName);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_CREATED, response.getResponseCode());
		JSONObject subfolder = new JSONObject(response.getText());
		IPath clonePath = new Path("file").append(project.getString(ProtocolConstants.KEY_ID)).append(fileName).makeAbsolute();
		clone(clonePath);

		String subfolderLocation = subfolder.getString(ProtocolConstants.KEY_LOCATION);
		URI subfolderUri = URI.create(subfolderLocation);
		IPath subfolderPath = new Path(subfolderUri.getPath()).removeFirstSegments(1); // remove /file
		Map<IPath, File> gitDirs = new HashMap<IPath, File>();
		GitUtils.getGitDirs(subfolderPath, gitDirs);
		assertFalse(gitDirs.isEmpty());
		gitDirs.clear();

		String projectLocation = project.getString(ProtocolConstants.KEY_CONTENT_LOCATION);
		URI projectUri = URI.create(projectLocation);
		IPath projectPath = new Path(projectUri.getPath()).removeFirstSegments(1); // remove /file
		GitUtils.getGitDirs(projectPath, gitDirs);
		assertFalse(gitDirs.isEmpty());
	}
}
