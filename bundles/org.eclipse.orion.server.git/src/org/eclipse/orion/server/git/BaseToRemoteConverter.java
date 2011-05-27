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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.*;
import org.eclipse.orion.server.git.servlets.GitServlet;

public abstract class BaseToRemoteConverter {

	public static final BaseToRemoteConverter FILE = new BaseToRemoteConverter() {
		@Override
		public URI baseToRemoteLocation(URI base, String remote, String branch) throws URISyntaxException {
			IPath p = new Path(base.getPath());
			p = new Path(GitServlet.GIT_URI).append(GitConstants.REMOTE_RESOURCE).append(remote).append(branch).addTrailingSeparator().append(p);
			return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), base.getQuery(), base.getFragment());
		};
	};

	public static final BaseToRemoteConverter REMOVE_FIRST_2 = new BaseToRemoteConverter() {
		@Override
		public URI baseToRemoteLocation(URI base, String remote, String branch) throws URISyntaxException {
			IPath p = new Path(base.getPath());
			p = p.uptoSegment(1).append(GitConstants.REMOTE_RESOURCE).append(remote).append(branch).addTrailingSeparator().append(p.removeFirstSegments(2));
			return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), base.getQuery(), base.getFragment());
		};
	};

	public static final BaseToRemoteConverter REMOVE_FIRST_3 = new BaseToRemoteConverter() {
		@Override
		public URI baseToRemoteLocation(URI base, String remote, String branch) throws URISyntaxException {
			IPath p = new Path(base.getPath());
			p = p.uptoSegment(1).append(GitConstants.REMOTE_RESOURCE).append(remote).append(branch).addTrailingSeparator().append(p.removeFirstSegments(3));
			return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), base.getQuery(), base.getFragment());
		};
	};

	public static final BaseToRemoteConverter REMOVE_FIRST_4 = new BaseToRemoteConverter() {
		@Override
		public URI baseToRemoteLocation(URI base, String remote, String branch) throws URISyntaxException {
			IPath p = new Path(base.getPath());
			p = p.uptoSegment(1).append(GitConstants.REMOTE_RESOURCE).append(remote).append(branch).addTrailingSeparator().append(p.removeFirstSegments(4));
			return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), base.getQuery(), base.getFragment());
		};
	};

	public static URI getRemoteBranchLocation(URI base, Repository db, BaseToRemoteConverter converter) throws IOException, URISyntaxException {
		Config repoConfig = db.getConfig();
		String branch = db.getBranch();
		String remote = repoConfig.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE);
		if (remote == null)
			// fall back to default remote
			remote = Constants.DEFAULT_REMOTE_NAME;
		String fetch = repoConfig.getString(ConfigConstants.CONFIG_REMOTE_SECTION, remote, "fetch"); //$NON-NLS-1$
		if (fetch != null) {
			// expecting something like: +refs/heads/*:refs/remotes/origin/*
			String[] split = fetch.split(":"); //$NON-NLS-1$
			if (split[0].endsWith("*") /*src*/&& split[1].endsWith("*") /*dst*/) { //$NON-NLS-1$ //$NON-NLS-2$
				return converter.baseToRemoteLocation(base, remote, branch);
			}
		}
		return null;
	}

	public abstract URI baseToRemoteLocation(URI base, String remote, String branch) throws URISyntaxException;
}
