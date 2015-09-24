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
package org.eclipse.orion.server.git.servlets;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.objects.LinkedFile;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitUtils {

	public enum Traverse {
		GO_UP, GO_DOWN, CURRENT
	}

	/*
	 * White list for URL schemes we can allow since they can't be used to gain access to git repositories in another
	 * Orion workspace since they require a daemon to serve them. Especially file protocol needs to be prohibited (bug
	 * 408270).
	 */
	private static Set<String> uriSchemeWhitelist = new HashSet<String>(Arrays.asList("ftp", "git", "http", "https", "sftp", "ssh"));

	/**
	 * Returns the file representing the Git repository directory for the given file path or any of its parent in the
	 * filesystem. If the file doesn't exits, is not a Git repository or an error occurred while transforming the given
	 * path into a store <code>null</code> is returned.
	 *
	 * @param path
	 *            expected format /file/{Workspace}/{projectName}[/{path}]
	 * @return the .git folder if found or <code>null</code> the give path cannot be resolved to a file or it's not
	 *         under control of a git repository
	 * @throws CoreException
	 * @throws IOException
	 */
	public static File getGitDir(IPath path) throws CoreException, IOException {
		Map<IPath, File> gitDirs = GitUtils.getGitDirs(path, Traverse.GO_UP);
		if (gitDirs == null)
			return null;
		Collection<File> values = gitDirs.values();
		if (values.isEmpty())
			return null;
		return values.toArray(new File[] {})[0];
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

	/**
	 * Returns the existing git repositories for the given file path, following the given traversal rule.
	 *
	 * @param path
	 *            expected format /file/{Workspace}/{projectName}[/{path}]
	 * @return a map of all git repositories found, or <code>null</code> if the provided path format doesn't match the
	 *         expected format.
	 * @throws CoreException
	 * @throws IOException
	 */
	public static Map<IPath, File> getGitDirs(IPath path, Traverse traverse) throws CoreException, IOException {
		IPath p = path.removeFirstSegments(1);// remove /file
		IFileStore fileStore = NewFileServlet.getFileStore(null, p);
		if (fileStore == null)
			return null;
		Map<IPath, File> result = new HashMap<IPath, File>();
		File file = fileStore.toLocalFile(EFS.NONE, null);
		// jgit can only handle a local file
		if (file == null)
			return result;
		switch (traverse) {
		case CURRENT:
			if (RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
				result.put(new Path(""), file); //$NON-NLS-1$
			} else if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
				result.put(new Path(""), new File(file, Constants.DOT_GIT)); //$NON-NLS-1$
			}
			break;
		case GO_UP:
			getGitDirsInParents(file, result);
			break;
		case GO_DOWN:
			getGitDirsInChildren(file, p, result);
			break;
		}
		return result;
	}

	public static Map<IPath, LinkedFile> getGitDirsWithSubmodules(IPath path) throws CoreException, IOException, GitAPIException {
		IPath p = path.removeFirstSegments(1);// remove /file
		IFileStore fileStore = NewFileServlet.getFileStore(null, p);
		if (fileStore == null)
			return null;
		Map<IPath, LinkedFile> result = new HashMap<IPath, LinkedFile>();
		File file = fileStore.toLocalFile(EFS.NONE, null);
		// jgit can only handle a local file
		if (file == null)
			return result;
		getGitDirsInChildrenSubmodule(file, p, result, null, null);
		return result;
	}

	private static void getGitDirsInParents(File file, Map<IPath, File> gitDirs) {
		int levelUp = 0;
		File workspaceRoot = null;
		try {
			workspaceRoot = OrionConfiguration.getRootLocation().toLocalFile(EFS.NONE, null);
		} catch (CoreException e) {
			Logger logger = LoggerFactory.getLogger(GitUtils.class);
			logger.error("Unable to get the root location", e);
			return;
		}
		if (workspaceRoot == null) {
			Logger logger = LoggerFactory.getLogger(GitUtils.class);
			logger.error("Unable to get the root location from " + OrionConfiguration.getRootLocation());
			return;
		}
		while (file != null && !file.getAbsolutePath().equals(workspaceRoot.getAbsolutePath())) {
			if (file.exists()) {
				if (RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
					gitDirs.put(getPathForLevelUp(levelUp), file);
					return;
				} else if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
					gitDirs.put(getPathForLevelUp(levelUp), new File(file, Constants.DOT_GIT));
					return;
				}
			}
			file = file.getParentFile();
			levelUp++;
		}
		return;
	}

	private static IPath getPathForLevelUp(int levelUp) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < levelUp; i++) {
			sb.append("../"); //$NON-NLS-1$
		}
		return new Path(sb.toString());
	}

	/**
	 * Recursively walks down a directory tree and collects the paths of all git repositories.
	 * 
	 * @throws IOException
	 */
	private static void getGitDirsInChildren(File localFile, IPath path, Map<IPath, File> gitDirs) throws CoreException, IOException {
		if (localFile.exists() && localFile.isDirectory()) {
			if (RepositoryCache.FileKey.isGitRepository(localFile, FS.DETECTED)) {
				gitDirs.put(path.addTrailingSeparator(), localFile);
			} else if (RepositoryCache.FileKey.isGitRepository(new File(localFile, Constants.DOT_GIT), FS.DETECTED)) {
				gitDirs.put(path.addTrailingSeparator(), new File(localFile, Constants.DOT_GIT));
			}
			File[] folders = localFile.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return file.isDirectory() && !file.getName().equals(Constants.DOT_GIT);
				}
			});
			for (File folder : folders) {
				getGitDirsInChildren(folder, path.append(folder.getName()), gitDirs);
			}
			return;
		}
	}

	private static void getGitDirsInChildrenSubmodule(File localFile, IPath path, Map<IPath, LinkedFile> gitDirs, File parent, IPath parentPath)
			throws CoreException, IOException, GitAPIException {
		if (localFile.exists() && localFile.isDirectory()) {
			if (RepositoryCache.FileKey.isGitRepository(localFile, FS.DETECTED)) {
				Map<IPath, File> submoduleFiles = new HashMap<IPath, File>();
				IPath localPath = path.addTrailingSeparator();
				LinkedFile currentLinkedFile;
				if (parent != null) {
					Entry<IPath, File> newParent = new AbstractMap.SimpleEntry<IPath, File>(parentPath, parent);
					currentLinkedFile = new LinkedFile(localFile, newParent);
				} else {
					currentLinkedFile = new LinkedFile(localFile);
				}
				File gitModule = new File(localFile, ".gitmodules");
				if (gitModule.exists() && !gitModule.isDirectory()) {
					FileInputStream fis = new FileInputStream(gitModule);
					BufferedReader br = new BufferedReader(new InputStreamReader(fis));
					String subPath = null;
					while ((subPath = br.readLine()) != null) {
						if (subPath.startsWith("[") && subPath.endsWith("]")) {
							subPath = br.readLine().split("=")[1].trim();
							File submoduleFile = new File(localFile, subPath);
							getGitDirsInChildrenSubmodule(submoduleFile, path.append(subPath), gitDirs, localFile, localPath);
							submoduleFiles.put(path.append(subPath), submoduleFile);
						}
					}
				}
				if (submoduleFiles.size() > 0) {
					currentLinkedFile.setChildren(submoduleFiles);
				}
				gitDirs.put(localPath, currentLinkedFile);

				return;
			} else if (RepositoryCache.FileKey.isGitRepository(new File(localFile, Constants.DOT_GIT), FS.DETECTED)) {
				Map<IPath, File> submoduleFiles = new HashMap<IPath, File>();
				IPath localPath = path.addTrailingSeparator();
				LinkedFile currentLinkedFile;
				if (parent != null) {
					Entry<IPath, File> newParent = new AbstractMap.SimpleEntry<IPath, File>(parentPath, parent);
					currentLinkedFile = new LinkedFile(new File(localFile, Constants.DOT_GIT), newParent);
				} else {
					currentLinkedFile = new LinkedFile(new File(localFile, Constants.DOT_GIT));
				}
				File gitModule = new File(localFile, ".gitmodules");
				if (gitModule.exists() && !gitModule.isDirectory()) {
					FileInputStream fis = new FileInputStream(gitModule);
					BufferedReader br = new BufferedReader(new InputStreamReader(fis));
					String subPath = null;
					while ((subPath = br.readLine()) != null) {
						if (subPath.startsWith("[") && subPath.endsWith("]")) {
							subPath = br.readLine().split("=")[1].trim();
							File submoduleFile = new File(localFile, subPath);
							getGitDirsInChildrenSubmodule(submoduleFile, path.append(subPath), gitDirs, localFile, localPath);
							submoduleFiles.put(path.append(subPath), submoduleFile);
						}
					}
				}

				if (submoduleFiles.size() > 0) {
					currentLinkedFile.setChildren(submoduleFiles);
				}
				gitDirs.put(localPath, currentLinkedFile);
				return;
			}
			File[] folders = localFile.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return file.isDirectory() && !file.getName().equals(Constants.DOT_GIT);
				}
			});
			for (File folder : folders) {
				getGitDirsInChildrenSubmodule(folder, path.append(folder.getName()), gitDirs, parent, parentPath);
			}
			return;
		}
	}

	public static String getRelativePath(IPath filePath, IPath pathToGitRoot) {
		StringBuilder sb = new StringBuilder();
		String file = null;
		if (!filePath.hasTrailingSeparator()) {
			file = filePath.lastSegment();
			filePath = filePath.removeLastSegments(1);
		}
		for (int i = 0; i < pathToGitRoot.segments().length; i++) {
			if (pathToGitRoot.segments()[i].equals(".."))
				sb.append(filePath.segment(filePath.segments().length - pathToGitRoot.segments().length + i)).append("/");
			// else TODO
		}
		if (file != null)
			sb.append(file);
		return sb.toString();
	}

	static GitCredentialsProvider createGitCredentialsProvider(final JSONObject json, HttpServletRequest request) {
		String username = json.optString(GitConstants.KEY_USERNAME, null);
		char[] password = json.optString(GitConstants.KEY_PASSWORD, "").toCharArray(); //$NON-NLS-1$
		String knownHosts = json.optString(GitConstants.KEY_KNOWN_HOSTS, null);
		byte[] privateKey = json.optString(GitConstants.KEY_PRIVATE_KEY, "").getBytes(); //$NON-NLS-1$
		byte[] publicKey = json.optString(GitConstants.KEY_PUBLIC_KEY, "").getBytes(); //$NON-NLS-1$
		byte[] passphrase = json.optString(GitConstants.KEY_PASSPHRASE, "").getBytes(); //$NON-NLS-1$

		GitCredentialsProvider cp = new GitCredentialsProvider(null /* set by caller */, request.getRemoteUser(), username, password, knownHosts);
		cp.setPrivateKey(privateKey);
		cp.setPublicKey(publicKey);
		cp.setPassphrase(passphrase);
		return cp;
	}

	public static String encode(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			// should never happen since "UTF-8" is used
		}
		return s;
	}

	public static String decode(String s) {
		try {
			return URLDecoder.decode(s, "UTF-8"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			// should never happen since "UTF-8" is used
		}
		return s;
	}

	/**
	 * Returns the existing WebProject corresponding to the provided path, or <code>null</code> if no such project
	 * exists.
	 * 
	 * @param path
	 *            path in the form /file/{workspaceId}/{projectName}/[filePath]
	 * @return the web project, or <code>null</code>
	 */
	public static ProjectInfo projectFromPath(IPath path) {
		if (path == null || path.segmentCount() < 3)
			return null;
		String workspaceId = path.segment(1);
		String projectName = path.segment(2);
		try {
			return OrionConfiguration.getMetaStore().readProject(workspaceId, projectName);
		} catch (CoreException e) {
			return null;
		}
	}

	/**
	 * Returns the HTTP path for the content resource of the given project.
	 * 
	 * @param workspace
	 *            The web workspace
	 * @param project
	 *            The web project
	 * @return the HTTP path of the project content resource
	 */
	public static IPath pathFromProject(WorkspaceInfo workspace, ProjectInfo project) {
		return new Path(org.eclipse.orion.internal.server.servlets.Activator.LOCATION_FILE_SERVLET).append(workspace.getUniqueId())
				.append(project.getFullName());

	}

	/**
	 * Returns whether or not the git repository URI is forbidden. If a scheme of the URI is matched, check if the
	 * scheme is a supported protocol. Otherwise, match for a scp-like ssh URI: [user@]host.xz:path/to/repo.git/ and
	 * ensure the URI does not represent a local file path.
	 * 
	 * @param uri
	 *            A git repository URI
	 * @return a boolean of whether or not the git repository URI is forbidden.
	 */
	public static boolean isForbiddenGitUri(URIish uri) {
		String scheme = uri.getScheme();
		String host = uri.getHost();
		String path = uri.getPath();
		boolean isForbidden = false;

		if (scheme != null) {
			isForbidden = !uriSchemeWhitelist.contains(scheme);
		} else {
			// match for a scp-like ssh URI
			if (host != null) {
				isForbidden = host.length() == 1 || path == null;
			} else {
				isForbidden = true;
			}
		}

		return isForbidden;
	}

	/**
	 * Returns whether the key gerrit.createchangeid is set to true in the git configuration
	 * 
	 * @param config
	 *            the configuration of the git repository
	 * @return true if the key gerrit.createchangeid is set to true
	 */
	public static boolean isGerrit(Config config, String remote) {
		String[] list = config.getStringList(ConfigConstants.CONFIG_REMOTE_SECTION, remote, GitConstants.KEY_IS_GERRIT.toLowerCase());
		for (int i = 0; i < list.length; i++) {
			if (list[i].equals("true")) {
				return true;
			}
		}
		return false;
	}

	public static void _testAllowFileScheme(boolean allow) {
		if (allow) {
			uriSchemeWhitelist.add("file"); //$NON-NLS-1$
		} else {
			uriSchemeWhitelist.remove("file"); //$NON-NLS-1$
		}
	}
}
