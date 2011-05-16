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
import java.io.FileFilter;
import java.util.Map;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;

public class GitUtils {
	/**
	 * Returns the file representing the Git repository directory for the given 
	 * file path or any of its parent in the filesystem. If the file doesn't exits,
	 * is not a Git repository or an error occurred while transforming the given
	 * path into a store <code>null</code> is returned.
	 *
	 * @param path expected format /file/{projectId}[/{path}]
	 * @return the .git folder if found or <code>null</code> the give path
	 * cannot be resolved to a file or it's not under control of a git repository
	 * @throws CoreException
	 */
	public static File getGitDir(IPath path) throws CoreException {
		IPath p = path.removeFirstSegments(1);
		IFileStore fileStore = NewFileServlet.getFileStore(p);
		if (fileStore == null)
			return null;
		File file = fileStore.toLocalFile(EFS.NONE, null);
		return getGitDir(file);
	}

	public static File getGitDir(File file) {
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

	public static void getGitDirs(IPath path, Map<IPath, File> gitDirs) throws CoreException {
		if (WebProject.exists(path.segment(0))) {
			WebProject webProject = WebProject.fromId(path.segment(0));
			IFileStore store = webProject.getProjectStore().getFileStore(path.removeFirstSegments(1));
			File file = store.toLocalFile(EFS.NONE, null);
			if (file.exists() && file.isDirectory()) {
				if (RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
					gitDirs.put(path, file);
					return;
				} else if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
					gitDirs.put(path, new File(file, Constants.DOT_GIT));
					return;
				}
				File[] folders = file.listFiles(new FileFilter() {
					public boolean accept(File file) {
						return file.isDirectory() && !file.getName().equals(Constants.DOT_GIT);
					}
				});
				for (File folder : folders) {
					getGitDirs(path.append(folder.getName()), gitDirs);
				}
				return;
			}
		}
	}
}
