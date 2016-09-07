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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitUtils {

	public enum Traverse {
		GO_UP, GO_DOWN, CURRENT
	}

	public static final String KNOWN_GITHUB_HOSTS = "orion.git.knownGithubHosts";

	/*
	 * White list for URL schemes we can allow since they can't be used to gain access to git repositories in another Orion workspace since they require a
	 * daemon to serve them. Especially file protocol needs to be prohibited (bug 408270).
	 */
	private static Set<String> uriSchemeWhitelist = new HashSet<String>(Arrays.asList("ftp", "git", "http", "https", "sftp", "ssh"));

	/**
	 * Returns the file representing the Git repository directory for the given file path or any of its parent in the filesystem. If the file doesn't exits, is
	 * not a Git repository or an error occurred while transforming the given path into a store <code>null</code> is returned.
	 *
	 * @param path
	 *            expected format /file/{Workspace}/{projectName}[/{path}]
	 * @return the .git folder if found or <code>null</code> the give path cannot be resolved to a file or it's not under control of a git repository
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
				File gitDir = resolveGitDir(file);
				if (gitDir != null) return gitDir;
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
	 * @return a map of all git repositories found, or <code>null</code> if the provided path format doesn't match the expected format.
	 * @throws CoreException
	 */
	public static Map<IPath, File> getGitDirs(IPath path, Traverse traverse) throws CoreException {
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
			File gitDir = resolveGitDir(file);
			if (gitDir != null) {
				result.put(new Path(""), gitDir); //$NON-NLS-1$
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
				File gitDir = resolveGitDir(file);
				if (gitDir != null && !gitDir.equals(file)) {
					gitDirs.put(getPathForLevelUp(levelUp), gitDir);
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
			File gitDir = resolveGitDir(localFile);
			if (gitDir != null) {
				gitDirs.put(path.addTrailingSeparator(), gitDir);
				return;
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
	 * Returns the existing WebProject corresponding to the provided path, or <code>null</code> if no such project exists.
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
		return new Path(org.eclipse.orion.internal.server.servlets.Activator.LOCATION_FILE_SERVLET).append(workspace.getUniqueId()).append(
				project.getFullName());

	}

	/**
	 * Returns whether or not the git repository URI is forbidden. If a scheme of the URI is matched, check if the scheme is a supported protocol. Otherwise,
	 * match for a scp-like ssh URI: [user@]host.xz:path/to/repo.git/ and ensure the URI does not represent a local file path.
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

	public static boolean isInGithub(String url) throws URISyntaxException {
		URIish uri = new URIish(url);
		String domain = uri.getHost();
		if(domain==null){
			return false;
		}
		if(domain.equals("github.com")){
			return true;
		}
		String known = PreferenceHelper.getString(KNOWN_GITHUB_HOSTS);
		if(known!=null){
			String[] knownHosts = known.split(",");
			for(String host : knownHosts){
				if(domain.equals(host)){
					return true;
				}
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

	public static String getCloneUrl(File gitDir) {
		Repository db = null;
		try {
			db = FileRepositoryBuilder.create(resolveGitDir(gitDir));
			return getCloneUrl(db);
		} catch (IOException e) {
			// ignore and skip Git URL
		} finally {
			if (db != null) {
				db.close();
			}
		}
		return null;
	}
	
	/**
	 * Returns the Git URL for a given git repository.
	 * @param db
	 * @return
	 */
	public static String getCloneUrl(Repository db) {
		StoredConfig config = db.getConfig();
		return config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL);
	}
	
	/**
	 * Returns a unique project name that does not exist in the given workspace, for the given clone name.
	 */
	public static String getUniqueProjectName(WorkspaceInfo workspace, String cloneName) {
		int i = 1;
		String uniqueName = cloneName;
		IMetaStore store = OrionConfiguration.getMetaStore();
		try {
			while (store.readProject(workspace.getUniqueId(), uniqueName) != null) {
				// add an incrementing counter suffix until we arrive at a unique name
				uniqueName = cloneName + '-' + ++i;
			}
		} catch (CoreException e) {
			// let it proceed with current name
		}
		return uniqueName;
	}
	/**
	 * Returns the file representing the Git repository directory for the given file path or any of its parent in the filesystem. If the file doesn't exits, is
	 * not a Git repository or an error occurred while transforming the given path into a store <code>null</code> is returned.
	 *
	 * @param file the file to check
	 * @return the .git folder if found or <code>null</code> the give path cannot be resolved to a file or it's not under control of a git repository
	 */
	public static File resolveGitDir(File file) {
		File dot = new File(file, Constants.DOT_GIT);
		if (RepositoryCache.FileKey.isGitRepository(dot, FS.DETECTED)) {
			return dot;
		} else if (dot.isFile()) {
			try {
				return getSymRef(file, dot, FS.DETECTED);
			} catch (IOException ignored) {
				// Continue searching if gitdir ref isn't found
			}
		} else if (RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
			return file;
		}
		return null;
	}
	public static String sanitizeCookie(String cookieString) {
		return cookieString.replaceAll("(\\r|\\n|%0[AaDd])", ""); //$NON-NLS-1$
	}

	//Note: these helpers are taken from JGit. There is no API in JGit <= 4.1 to resolve sym refs.
	private static boolean isSymRef(byte[] ref) {
		if (ref.length < 9)
			return false;
		return /**/ref[0] == 'g' //
				&& ref[1] == 'i' //
				&& ref[2] == 't' //
				&& ref[3] == 'd' //
				&& ref[4] == 'i' //
				&& ref[5] == 'r' //
				&& ref[6] == ':' //
				&& ref[7] == ' ';
	}
	private static File getSymRef(File workTree, File dotGit, FS fs)
			throws IOException {
		byte[] content = IO.readFully(dotGit);
		if (!isSymRef(content))
			throw new IOException(MessageFormat.format(
					JGitText.get().invalidGitdirRef, dotGit.getAbsolutePath()));

		int pathStart = 8;
		int lineEnd = RawParseUtils.nextLF(content, pathStart);
		if (content[lineEnd - 1] == '\n')
			lineEnd--;
		if (lineEnd == pathStart)
			throw new IOException(MessageFormat.format(
					JGitText.get().invalidGitdirRef, dotGit.getAbsolutePath()));

		String gitdirPath = RawParseUtils.decode(content, pathStart, lineEnd);
		File gitdirFile = fs.resolve(workTree, gitdirPath);
		if (gitdirFile.isAbsolute())
			return gitdirFile;
		else
			return new File(workTree, gitdirPath).getCanonicalFile();
	}
}
