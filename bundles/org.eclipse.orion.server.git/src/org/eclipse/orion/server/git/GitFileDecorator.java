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
package org.eclipse.orion.server.git;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler.Method;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.git.objects.*;
import org.eclipse.orion.server.git.objects.Status;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.json.*;

/**
 * Adds links to workspace and file resources referring to related git resources.
 */
public class GitFileDecorator implements IWebResourceDecorator {

	@Override
	public void addAtributesFor(HttpServletRequest request, URI resource, JSONObject representation) {
		String requestPath = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
		IPath targetPath = new Path(requestPath);
		if (targetPath.segmentCount() <= 1)
			return;
		String servlet = request.getServletPath();
		if (!"/file".equals(servlet) && !"/workspace".equals(servlet)) //$NON-NLS-1$ //$NON-NLS-2$
			return;

		boolean isWorkspace = ("/workspace".equals(servlet)); //$NON-NLS-1$

		try {
			if (isWorkspace && Method.POST.equals(Method.fromString(request.getMethod()))) {
				String contentLocation = representation.getString(ProtocolConstants.KEY_CONTENT_LOCATION);

				// initialize a new git repository on project creation if specified by configuration
				initGitRepository(request, targetPath, representation);
				addGitLinks(request, new URI(contentLocation), representation);
				return;
			}

			if (isWorkspace && Method.GET.equals(Method.fromString(request.getMethod()))) {
				JSONArray children = representation.optJSONArray(ProtocolConstants.KEY_CHILDREN);
				if (children != null) {
					for (int i = 0; i < children.length(); i++) {
						JSONObject child = children.getJSONObject(i);
						String location = child.getString(ProtocolConstants.KEY_LOCATION);
						addGitLinks(request, new URI(location), child);
					}
				}
				return;
			}

			if (!isWorkspace && Method.GET.equals(Method.fromString(request.getMethod()))) {
				boolean git = false;
				addGitLinks(request, resource, representation);

				JSONArray children = representation.optJSONArray(ProtocolConstants.KEY_CHILDREN);
				if (children != null) {
					for (int i = 0; i < children.length(); i++) {
						JSONObject child = children.getJSONObject(i);
						String location = child.getString(ProtocolConstants.KEY_LOCATION);
						// assumption that Git resources may live only under another Git resource
						if (git || GitUtils.getGitDir(targetPath.append(child.getString(ProtocolConstants.KEY_NAME))) != null)
							addGitLinks(request, new URI(location), child);
					}
				}
			}
		} catch (Exception e) {
			// log and continue
			LogHelper.log(e);
		}
	}

	private void addGitLinks(HttpServletRequest request, URI location, JSONObject representation) throws URISyntaxException, JSONException, CoreException, IOException {
		IPath targetPath = new Path(location.getPath());
		IPath requestPath = targetPath;

		if (request.getContextPath().length() != 0) {
			IPath contextPath = new Path(request.getContextPath());
			if (contextPath.isPrefixOf(targetPath)) {
				requestPath = targetPath.removeFirstSegments(contextPath.segmentCount());
			}
		}

		File gitDir = GitUtils.getGitDir(requestPath);
		if (gitDir == null)
			return;

		Repository db = new FileRepository(gitDir);
		URI cloneLocation = BaseToCloneConverter.getCloneLocation(location, BaseToCloneConverter.FILE);

		JSONObject gitSection = new JSONObject();

		// add Git Diff URI
		IPath path = new Path(GitServlet.GIT_URI + '/' + Diff.RESOURCE + '/' + GitConstants.KEY_DIFF_DEFAULT).append(targetPath);
		URI link = new URI(location.getScheme(), location.getAuthority(), request.getContextPath() + path.toString(), null, null);
		gitSection.put(GitConstants.KEY_DIFF, link);

		// add Git Status URI
		path = new Path(GitServlet.GIT_URI + '/' + Status.RESOURCE).append(targetPath);
		link = new URI(location.getScheme(), location.getAuthority(), request.getContextPath() + path.toString(), null, null);
		gitSection.put(GitConstants.KEY_STATUS, link);

		// add Git Index URI
		path = new Path(GitServlet.GIT_URI + '/' + Index.RESOURCE).append(targetPath);
		link = new URI(location.getScheme(), location.getAuthority(), request.getContextPath() + path.toString(), null, null);
		gitSection.put(GitConstants.KEY_INDEX, link);

		// add Git HEAD URI
		path = new Path(GitServlet.GIT_URI + '/' + Commit.RESOURCE).append(Constants.HEAD).append(targetPath);
		link = new URI(location.getScheme(), location.getAuthority(), request.getContextPath() + path.toString(), null, null);
		gitSection.put(GitConstants.KEY_HEAD, link);

		// add Git Commit URI
		path = new Path(GitServlet.GIT_URI + '/' + Commit.RESOURCE).append(db.getBranch()).append(targetPath);
		link = new URI(location.getScheme(), location.getAuthority(), request.getContextPath() + path.toString(), null, null);
		gitSection.put(GitConstants.KEY_COMMIT, link);

		// add Git Remote URI
		path = new Path(GitServlet.GIT_URI + '/' + Remote.RESOURCE).append(targetPath);
		link = new URI(location.getScheme(), location.getAuthority(), request.getContextPath() + path.toString(), null, null);
		gitSection.put(GitConstants.KEY_REMOTE, link);

		// add Git Clone Config URI
		path = new Path(GitServlet.GIT_URI + '/' + ConfigOption.RESOURCE + '/' + Clone.RESOURCE).append(targetPath);
		link = new URI(location.getScheme(), location.getAuthority(), request.getContextPath() + path.toString(), null, null);
		gitSection.put(GitConstants.KEY_CONFIG, link);

		// add Git Default Remote Branch URI 
		Remote defaultRemote = new Remote(cloneLocation, db, Constants.DEFAULT_REMOTE_NAME);
		RemoteBranch defaultRemoteBranch = new RemoteBranch(cloneLocation, db, defaultRemote, db.getBranch());
		gitSection.put(GitConstants.KEY_DEFAULT_REMOTE_BRANCH, defaultRemoteBranch.getLocation());

		// add Git Tag URI
		path = new Path(GitServlet.GIT_URI + '/' + Tag.RESOURCE).append(targetPath);
		link = new URI(location.getScheme(), location.getAuthority(), request.getContextPath() + path.toString(), null, null);
		gitSection.put(GitConstants.KEY_TAG, link);

		// add Git Clone URI
		gitSection.put(GitConstants.KEY_CLONE, cloneLocation);

		representation.put(GitConstants.KEY_GIT, gitSection);
	}

	/**
	 * If this  server is configured to use git by default, then each project creation that is not
	 * already in a repository needs to have a git -init performed to initialize the repository.
	 */
	private void initGitRepository(HttpServletRequest request, IPath targetPath, JSONObject representation) {
		//project creation URL is of the form POST /workspace/<id>
		if (!(targetPath.segmentCount() == 2 && "workspace".equals(targetPath.segment(0)) && Method.POST.equals(Method.fromString(request.getMethod())))) //$NON-NLS-1$
			return;
		String scm = PreferenceHelper.getString(ServerConstants.CONFIG_FILE_DEFAULT_SCM, "").toLowerCase(); //$NON-NLS-1$
		if (!"git".equals(scm)) //$NON-NLS-1$
			return;
		try {
			IFileStore store = WebProject.fromId(representation.optString(ProtocolConstants.KEY_ID)).getProjectStore();
			//create repository in each project if it doesn't already exist
			File localFile = store.toLocalFile(EFS.NONE, null);
			File gitDir = GitUtils.getGitDir(localFile);
			if (gitDir == null) {
				gitDir = new File(localFile, Constants.DOT_GIT);
				FileRepository repo = new FileRepositoryBuilder().setGitDir(gitDir).build();
				repo.create();
				//we need to perform an initial commit to workaround JGit bug 339610.
				Git git = new Git(repo);
				git.add().addFilepattern(".").call(); //$NON-NLS-1$
				git.commit().setMessage("Initial commit").call();
			}
		} catch (Exception e) {
			//just log it - this is not the purpose of the file decorator
			LogHelper.log(e);
		}
	}
}
