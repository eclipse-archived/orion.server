/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Blame;
import org.eclipse.orion.server.git.objects.Branch;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.objects.Commit;
import org.eclipse.orion.server.git.objects.ConfigOption;
import org.eclipse.orion.server.git.objects.Diff;
import org.eclipse.orion.server.git.objects.Ignore;
import org.eclipse.orion.server.git.objects.Index;
import org.eclipse.orion.server.git.objects.PullRequest;
import org.eclipse.orion.server.git.objects.Remote;
import org.eclipse.orion.server.git.objects.Stash;
import org.eclipse.orion.server.git.objects.Status;
import org.eclipse.orion.server.git.objects.Submodule;
import org.eclipse.orion.server.git.objects.Tag;
import org.eclipse.orion.server.git.objects.Tree;

/**
 * A git handler for Orion Git API v 1.0.
 */
public class GitHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<String> branchHandlerV1;
	private ServletResourceHandler<String> cloneHandlerV1;
	private ServletResourceHandler<String> commitHandlerV1;
	private ServletResourceHandler<String> configHandlerV1;
	private ServletResourceHandler<String> diffHandlerV1;
	private ServletResourceHandler<String> indexHandlerV1;
	private ServletResourceHandler<String> ignoreHandlerV1;
	private ServletResourceHandler<String> remoteHandlerV1;
	private ServletResourceHandler<String> statusHandlerV1;
	private ServletResourceHandler<String> tagHandlerV1;
	private ServletResourceHandler<String> blameHandlerV1;
	private ServletResourceHandler<String> treeHandlerV1;
	private ServletResourceHandler<String> stashHandlerV1;
	private ServletResourceHandler<String> submoduleHandlerV1;
	private ServletResourceHandler<String> pullRequestHandlerV1;
	
	GitHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		branchHandlerV1 = new GitBranchHandlerV1(statusHandler);
		blameHandlerV1 = new GitBlameHandlerV1(statusHandler);
		cloneHandlerV1 = new GitCloneHandlerV1(statusHandler);
		commitHandlerV1 = new GitCommitHandlerV1(statusHandler);
		configHandlerV1 = new GitConfigHandlerV1(statusHandler);
		diffHandlerV1 = new GitDiffHandlerV1(statusHandler);
		indexHandlerV1 = new GitIndexHandlerV1(statusHandler);
		ignoreHandlerV1 = new GitIgnoreHandlerV1(statusHandler);
		remoteHandlerV1 = new GitRemoteHandlerV1(statusHandler);
		statusHandlerV1 = new GitStatusHandlerV1(statusHandler);
		tagHandlerV1 = new GitTagHandlerV1(statusHandler);
		treeHandlerV1 = new GitTreeHandlerV1(statusHandler);
		stashHandlerV1 = new GitStashHandlerV1(statusHandler);
		submoduleHandlerV1 = new GitSubmoduleHandlerV1(statusHandler);
		pullRequestHandlerV1 = new GitPullRequestHandlerV1(statusHandler);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String gitPathInfo) throws ServletException {

		String[] infoParts = gitPathInfo.split("\\/", 3); //$NON-NLS-1$
		if (infoParts.length < 2)
			return false; // malformed request, we don't know how to handle this

		String pathString = infoParts.length > 2 ? infoParts[2] : "";
		if (request.getContextPath().length() != 0) {
			IPath path = pathString == null ? Path.EMPTY : new Path(pathString);
			IPath contextPath = new Path(request.getContextPath());
			if (contextPath.isPrefixOf(path)) {
				pathString = path.removeFirstSegments(contextPath.segmentCount()).toString();
			}
		}

		// TODO: Add to constants
		String tokenName = PreferenceHelper.getString("ltpa.token.name"); //$NON-NLS-1$
		if (tokenName != null) {
			javax.servlet.http.Cookie[] cookies = request.getCookies();
			if (cookies != null) {
				for (int i = 0; i < cookies.length; i++) {
					Cookie currentCookie = cookies[i];
					if (tokenName.equals(currentCookie.getName())) {
						Cookie loginCookie = new Cookie(currentCookie.getName(), GitUtils.sanitizeCookie(currentCookie.getValue()));
						request.setAttribute(GitConstants.KEY_SSO_TOKEN, loginCookie);
					}
				}
			}
		}

		if (infoParts[1].equals(Branch.RESOURCE)) {
			return branchHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Clone.RESOURCE)) {
			return cloneHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Commit.RESOURCE)) {
			return commitHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(ConfigOption.RESOURCE)) {
			return configHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Diff.RESOURCE)) {
			return diffHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Index.RESOURCE)) {
			return indexHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Remote.RESOURCE)) {
			return remoteHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Status.RESOURCE)) {
			return statusHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Tag.RESOURCE)) {
			return tagHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Blame.RESOURCE)) {
			return blameHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Ignore.RESOURCE)) {
			return ignoreHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Tree.RESOURCE)) {
			return treeHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Stash.RESOURCE)) {
			return stashHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Submodule.RESOURCE)) {
			return submoduleHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(PullRequest.RESOURCE)) {
			return pullRequestHandlerV1.handleRequest(request, response, pathString);
		}

		return false;
	}
}
