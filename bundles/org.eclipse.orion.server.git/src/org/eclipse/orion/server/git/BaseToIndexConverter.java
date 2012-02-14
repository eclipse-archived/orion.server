/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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
import org.eclipse.orion.server.git.objects.Index;
import org.eclipse.orion.server.git.servlets.GitServlet;

public abstract class BaseToIndexConverter {

	public static final BaseToIndexConverter CLONE = new BaseToIndexConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).removeFirstSegments(2).makeRelative();
		};
	};

	public static URI getIndexLocation(URI base, BaseToIndexConverter converter) throws URISyntaxException {
		IPath filePath = converter.getFilePath(base);
		IPath clonePath = findClonePath(filePath);
		if (clonePath == null)
			return null;
		IPath p = new Path(GitServlet.GIT_URI).append(Index.RESOURCE).append("file").append(clonePath).addTrailingSeparator(); //$NON-NLS-1$
		return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), base.getQuery(), base.getFragment());
	}

	public abstract IPath getFilePath(URI base) throws URISyntaxException;

	private static IPath findClonePath(IPath filePath) {
		// /file/{projectId}[/{path}] -> /{projectId}[/{path}]
		IPath p = filePath.removeFirstSegments(1);
		while (p.segmentCount() > 0) {
			IFileStore fileStore = NewFileServlet.getFileStore(p);
			if (fileStore == null)
				return null;
			File file;
			try {
				file = fileStore.toLocalFile(EFS.NONE, null);
			} catch (CoreException e) {
				return null;
			}
			if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
				return p;
			}
			p = p.removeLastSegments(1);
		}
		return null;
	}
}