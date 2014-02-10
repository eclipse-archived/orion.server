/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import javax.servlet.http.Cookie;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.core.Activator;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.json.JSONObject;

public class GitUtils {

	public enum Traverse {
		GO_UP, GO_DOWN, CURRENT
	}

	/*
	 * White list for URL schemes we can allow since they can't be used to gain access to git repositories
	 * in another Orion workspace since they require a daemon to serve them. Especially file protocol needs
	 * to be prohibited (bug 408270).
	 */
	private static Set<String> uriSchemeWhitelist = new HashSet<String>(Arrays.asList("ftp", "git", "http", "https", "sftp", "ssh"));

	/**
	 * Returns the file representing the Git repository directory for the given
	 * file path or any of its parent in the filesystem. If the file doesn't exits,
	 * is not a Git repository or an error occurred while transforming the given
	 * path into a store <code>null</code> is returned.
	 *
	 * @param path expected format /file/{Workspace}/{projectName}[/{path}]
	 * @return the .git folder if found or <code>null</code> the give path
	 * cannot be resolved to a file or it's not under control of a git repository
	 * @throws CoreException
	 */
	public static File getGitDir(IPath path) throws CoreException {
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
	 * Returns the existing git repositories for the given file path, following
	 * the given traversal rule.
	 *
	 * @param path expected format /file/{Workspace}/{projectName}[/{path}]
	 * @return a map of all git repositories found, or <code>null</code>
	 * if the provided path format doesn't match the expected format.
	 * @throws CoreException
	 */
	public static Map<IPath, File> getGitDirs(IPath path, Traverse traverse) throws CoreException {
		IPath p = path.removeFirstSegments(1);//remove /file
		IFileStore fileStore = NewFileServlet.getFileStore(null, p);
		if (fileStore == null)
			return null;
		Map<IPath, File> result = new HashMap<IPath, File>();
		File file = fileStore.toLocalFile(EFS.NONE, null);
		//jgit can only handle a local file
		if (file == null)
			return result;
		switch (traverse) {
			case CURRENT :
				if (RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
					result.put(new Path(""), file); //$NON-NLS-1$
				} else if (RepositoryCache.FileKey.isGitRepository(new File(file, Constants.DOT_GIT), FS.DETECTED)) {
					result.put(new Path(""), new File(file, Constants.DOT_GIT)); //$NON-NLS-1$
				}
				break;
			case GO_UP :
				getGitDirsInParents(file, result);
				break;
			case GO_DOWN :
				getGitDirsInChildren(file, p, result);
				break;
		}
		return result;
	}

	private static void getGitDirsInParents(File file, Map<IPath, File> gitDirs) {
		int levelUp = 0;
		File workspaceRoot = Activator.getDefault().getPlatformLocation().toFile();
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
	 */
	private static void getGitDirsInChildren(File localFile, IPath path, Map<IPath, File> gitDirs) throws CoreException {
		if (localFile.exists() && localFile.isDirectory()) {
			if (RepositoryCache.FileKey.isGitRepository(localFile, FS.DETECTED)) {
				gitDirs.put(path.addTrailingSeparator(), localFile);
				return;
			} else if (RepositoryCache.FileKey.isGitRepository(new File(localFile, Constants.DOT_GIT), FS.DETECTED)) {
				gitDirs.put(path.addTrailingSeparator(), new File(localFile, Constants.DOT_GIT));
				return;
			}
			File[] folders = localFile.listFiles(new FileFilter() {
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

	static GitCredentialsProvider createGitCredentialsProvider(final JSONObject json) {
		String username = json.optString(GitConstants.KEY_USERNAME, null);
		char[] password = json.optString(GitConstants.KEY_PASSWORD, "").toCharArray(); //$NON-NLS-1$
		String knownHosts = json.optString(GitConstants.KEY_KNOWN_HOSTS, null);
		byte[] privateKey = json.optString(GitConstants.KEY_PRIVATE_KEY, "").getBytes(); //$NON-NLS-1$
		byte[] publicKey = json.optString(GitConstants.KEY_PUBLIC_KEY, "").getBytes(); //$NON-NLS-1$
		byte[] passphrase = json.optString(GitConstants.KEY_PASSPHRASE, "").getBytes(); //$NON-NLS-1$

		GitCredentialsProvider cp = new GitCredentialsProvider(null /* set by caller */, username, password, knownHosts);
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
	 * Returns the existing WebProject corresponding to the provided path,
	 * or <code>null</code> if no such project exists.
	 * @param path path in the form /file/{workspaceId}/{projectName}/[filePath]
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
	 * @param workspace The web workspace
	 * @param project The web project
	 * @return the HTTP path of the project content resource
	 */
	public static IPath pathFromProject(WorkspaceInfo workspace, ProjectInfo project) {
		return new Path(org.eclipse.orion.internal.server.servlets.Activator.LOCATION_FILE_SERVLET).append(workspace.getUniqueId()).append(project.getFullName());

	}

	/**
	 * Returns whether or not the git repository URI is forbidden. If a scheme of the URI is matched, check if the scheme
	 * is a supported protocol. Otherwise, match for a scp-like ssh URI: [user@]host.xz:path/to/repo.git/ and ensure the URI
	 * does not represent a local file path.
	 * @param uri A git repository URI
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
	 * @param config the configuration of the git repository
	 * @return true if the key gerrit.createchangeid is set to true
	 */
	public static boolean isGerrit(Config config) {
		return config.getBoolean(ConfigConstants.CONFIG_GERRIT_SECTION, ConfigConstants.CONFIG_KEY_CREATECHANGEID, false);
	}

	public static void _testAllowFileScheme(boolean allow) {
		if (allow) {
			uriSchemeWhitelist.add("file"); //$NON-NLS-1$
		} else {
			uriSchemeWhitelist.remove("file"); //$NON-NLS-1$
		}
	}

	//Used for enabling SSO git operations over http
	private static java.lang.reflect.Method SET_SSO_METHOD;
	private static boolean SET_SSO_METHOD_INITIALIZED;
	//Touch the Transport class so it gets initalized before TransportHttp
	//	private static final boolean FORCE_INITIALIZE = Transport.DEFAULT_PUSH_THIN;
	private static final RefSpec FORCE_INITIALIZE = Transport.REFSPEC_TAGS;

	public static void setSSOToken(Cookie c) {
		if (SET_SSO_METHOD == null && !SET_SSO_METHOD_INITIALIZED) {
			try {
				SET_SSO_METHOD_INITIALIZED = true;
				SET_SSO_METHOD = TransportHttp.class.getMethod("setSSOToken", Cookie.class);
			} catch (RuntimeException e) {
				//				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "RuntimeException while trying to look up JGit call", e));
			} catch (NoSuchMethodException e) {
				//				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "NoSuchMethodException while trying to look up JGit call", e));
			}
		}
		if (SET_SSO_METHOD != null) {
			try {
				SET_SSO_METHOD.invoke(null, c);
			} catch (IllegalArgumentException e) {
				//				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "IllegalArgumentException while trying to set token", e));
			} catch (IllegalAccessException e) {
				//				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "IllegalAccessException while trying to set token", e));
			} catch (InvocationTargetException e) {
				//				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "InvocationTargetException while trying to set token", e));
			}
		}
	}

	private static java.lang.reflect.Method GET_SSO_METHOD;
	private static boolean GET_SSO_METHOD_INITIALIZED;

	public static Cookie getSSOToken() {
		if (GET_SSO_METHOD == null && !GET_SSO_METHOD_INITIALIZED) {
			try {
				GET_SSO_METHOD_INITIALIZED = true;
				GET_SSO_METHOD = TransportHttp.class.getMethod("getSSOToken");
			} catch (RuntimeException e) {
				//				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "RuntimeException while trying to look up JGit call", e));
			} catch (NoSuchMethodException e) {
				//				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "NoSuchMethodException while trying to look up JGit call", e));
			}
		}
		if (GET_SSO_METHOD != null) {
			try {
				return (Cookie) GET_SSO_METHOD.invoke(null);
			} catch (IllegalArgumentException e) {
				//				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "IllegalArgumentException while trying to set token", e));
			} catch (IllegalAccessException e) {
				//				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "IllegalAccessException while trying to set token", e));
			} catch (InvocationTargetException e) {
				//				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "InvocationTargetException while trying to set token", e));
			}
		}
		return null;
	}
}
