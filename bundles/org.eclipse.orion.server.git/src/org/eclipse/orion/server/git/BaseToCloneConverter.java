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
import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.servlets.GitServlet;

public abstract class BaseToCloneConverter {

	public static final BaseToCloneConverter FILE = new BaseToCloneConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).makeRelative();
		};
	};

	public static final BaseToCloneConverter STATUS = new BaseToCloneConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).removeFirstSegments(2).makeRelative();
		};
	};

	public static final BaseToCloneConverter COMMIT = STATUS;

	public static final BaseToCloneConverter REMOTE_LIST = STATUS;

	public static final BaseToCloneConverter BRANCH_LIST = STATUS;

	public static final BaseToCloneConverter TAG_LIST = STATUS;

	public static final BaseToCloneConverter REMOTE = new BaseToCloneConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).removeFirstSegments(3).makeRelative();
		};
	};

	public static final BaseToCloneConverter BRANCH = REMOTE;

	public static final BaseToCloneConverter COMMIT_REFRANGE = REMOTE;

	public static final BaseToCloneConverter CONFIG = REMOTE;

	public static final BaseToCloneConverter DIFF = REMOTE;

	public static final BaseToCloneConverter TAG = REMOTE;

	public static final BaseToCloneConverter REMOTE_BRANCH = new BaseToCloneConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).removeFirstSegments(4).makeRelative();
		};
	};

	public static final BaseToCloneConverter CONFIG_OPTION = REMOTE_BRANCH;

	public static URI getCloneLocation(URI base, BaseToCloneConverter converter) throws URISyntaxException, CoreException {
		IPath filePath = converter.getFilePath(base);
		IPath clonePath = findClonePath(filePath);
		if (clonePath == null)
			return null;
		IPath p = new Path(GitServlet.GIT_URI).append(Clone.RESOURCE).append("file").append(clonePath).addTrailingSeparator(); //$NON-NLS-1$
		return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), base.getQuery(), base.getFragment());
	}

	public abstract IPath getFilePath(URI base) throws URISyntaxException;

	private static IPath findClonePath(IPath filePath) throws CoreException {
		// /file/{projectId}[/{path}] -> /{projectId}[/{path}]
		IPath p = filePath.removeFirstSegments(1);
		while (p.segmentCount() > 0) {
			IFileStore fileStore = NewFileServlet.getFileStore(p);
			if (fileStore == null)
				return null;
			File file = fileStore.toLocalFile(EFS.NONE, null);
			if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
				return p;
			}
			p = p.removeLastSegments(1);
		}
		return null;
	}
}
