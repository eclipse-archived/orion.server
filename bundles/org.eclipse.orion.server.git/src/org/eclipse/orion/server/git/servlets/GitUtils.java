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
package org.eclipse.orion.server.git.servlets;

import java.io.File;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;

public class GitUtils {
	/**
	 * Returns the file representing the Git repository directory for the given 
	 * file path or <code>null</code> if the file doesn't exits, is not a Git 
	 * repository or an error occurred while transforming the given path into 
	 * a store.  
	 */
	public static File getGitDir(IPath path) throws CoreException {
		IPath p = path.removeFirstSegments(1);
		IFileStore fileStore = NewFileServlet.getFileStore(p);
		if (fileStore == null)
			return null;
		File file = fileStore.toLocalFile(EFS.NONE, null);
		return getGitDirForFile(file);
	}

	public static File getGitDirForFile(File file) {
		if (file.exists()) {
			while (file != null) {
				if (RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
					return file;
				} else if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
					return new File(file, Constants.DOT_GIT);
				}
				file = file.getParentFile();
			}
		}
		return null;
	}
}
