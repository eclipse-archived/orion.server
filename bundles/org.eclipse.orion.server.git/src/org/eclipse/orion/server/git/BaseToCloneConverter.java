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
package org.eclipse.orion.server.git;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.orion.server.git.servlets.GitUtils;

public abstract class BaseToCloneConverter {

	public static final BaseToCloneConverter FILE = new BaseToCloneConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).makeRelative();
		}
	};

	public static final BaseToCloneConverter STATUS = new BaseToCloneConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).removeFirstSegments(2).makeRelative();
		}
	};

	public static final BaseToCloneConverter COMMIT = STATUS;

	public static final BaseToCloneConverter REMOTE_LIST = STATUS;

	public static final BaseToCloneConverter BRANCH_LIST = STATUS;

	public static final BaseToCloneConverter TAG_LIST = STATUS;

	public static final BaseToCloneConverter REMOTE = new BaseToCloneConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).removeFirstSegments(3).makeRelative();
		}
	};

	public static final BaseToCloneConverter BRANCH = REMOTE;

	public static final BaseToCloneConverter COMMIT_REFRANGE = REMOTE;

	public static final BaseToCloneConverter CONFIG = REMOTE;

	public static final BaseToCloneConverter DIFF = REMOTE;

	public static final BaseToCloneConverter TAG = REMOTE;

	public static final BaseToCloneConverter BLAME = REMOTE;

	public static final BaseToCloneConverter REMOTE_BRANCH = new BaseToCloneConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).removeFirstSegments(4).makeRelative();
		}
	};

	public static final BaseToCloneConverter CONFIG_OPTION = REMOTE_BRANCH;

	public static URI getCloneLocation(URI base, BaseToCloneConverter converter) throws URISyntaxException, CoreException {
		IPath filePath = converter.getFilePath(base);
		IPath clonePath = findClonePath(filePath);
		if (clonePath == null)
			return null;
		IPath p = new Path(GitServlet.GIT_URI).append(Clone.RESOURCE).append(clonePath).addTrailingSeparator();
		return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), base.getQuery(), base.getFragment());
	}

	public abstract IPath getFilePath(URI base) throws URISyntaxException;

	private static IPath findClonePath(IPath filePath) throws CoreException {
		Map<IPath, File> dirs = GitUtils.getGitDirs(filePath, GitUtils.Traverse.GO_UP);
		// going up, there can only be one git repository
		if (dirs.size() != 1)
			return null;
		IPath relativePath = dirs.keySet().iterator().next();
		return filePath.append(relativePath);
	}
}
