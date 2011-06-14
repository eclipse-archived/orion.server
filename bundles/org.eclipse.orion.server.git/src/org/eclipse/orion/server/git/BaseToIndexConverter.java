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
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.git.servlets.GitServlet;

public abstract class BaseToIndexConverter {

	public static final BaseToIndexConverter FILE = new BaseToIndexConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).makeRelative();
		};
	};

	public static final BaseToIndexConverter REMOTE = new BaseToIndexConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).removeFirstSegments(3).makeRelative();
		};
	};

	public static URI getIndexLocation(URI base, BaseToIndexConverter converter) throws IOException, URISyntaxException, CoreException {
		IPath filePath = converter.getFilePath(base);
		IPath clonePath = findClonePath(filePath);
		if (clonePath == null)
			return null;
		IPath p = new Path(GitServlet.GIT_URI).append(GitConstants.INDEX_RESOURCE).append("file").append(clonePath).addTrailingSeparator();
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
