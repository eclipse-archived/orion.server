/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.filesystem.git;

import java.io.*;
import java.net.*;
import java.util.Collection;
import org.apache.commons.io.FileUtils;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.filesystem.provider.FileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.filesystem.git.*;
import org.slf4j.LoggerFactory;

/**
 * File system implementation that writes to a git repository.
 */
public class GitFileStore extends FileStore {

	private String authority;

	private URIish uri;
	private IPath path;
	private Repository localRepo;

	private static byte[] readBytes(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int r;
		byte[] data = new byte[16384];
		while ((r = is.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, r);
		}
		buffer.flush();
		return buffer.toByteArray();
	}

	public GitFileStore(URIish u, IPath p, String authority) {
		if (u == null)
			throw new IllegalArgumentException("uriish cannot be null");
		if (authority == null)
			throw new IllegalArgumentException("authority cannot be null");
		if (p == null)
			throw new IllegalArgumentException("path cannot be null");

		this.uri = u;
		this.authority = authority;
		this.path = p;
	}

	private boolean canInit() {
		// org.eclipse.jgit.transport.TransportLocal.canHandle(URIish, FS)
		if (uri.getHost() != null || uri.getPort() > 0 || uri.getUser() != null
				|| uri.getPass() != null || uri.getPath() == null)
			return false;

		if ("file".equals(uri.getScheme()) || uri.getScheme() == null)
			return true;
		return false;
	}

	public IFileInfo[] childInfos(int options, IProgressMonitor monitor) throws CoreException {
		IFileStore[] childStores = childStores(options, monitor);
		IFileInfo[] childInfos = new IFileInfo[childStores.length];
		for (int i = 0; i < childStores.length; i++) {
			childInfos[i] = childStores[i].fetchInfo(EFS.CACHE /* don't pull */, monitor);
		}
		return childInfos;
	}

	@Override
	public String[] childNames(int options, IProgressMonitor monitor)
			throws CoreException {
		if (!isCloned()) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, 1, "A private clone for " + this + " doesn't exist." + getLocalFile(), null));
		}
		pull();
		File f = getLocalFile();
		return f.list(null);
	}

	private void clone(IProgressMonitor monitor) throws CoreException {
		try {
			File workdir = getWorkingDir();
			if (!isCloned()) {
				workdir.mkdirs();

				CloneCommand cc = Git.cloneRepository();
				cc.setBare(false);
				cc.setBranch(Constants.R_HEADS + Constants.MASTER);
				cc.setCredentialsProvider(getCredentialsProvider());
				cc.setDirectory(workdir);
				// cc.setProgressMonitor(monitor);
				// cc.setRemote(Constants.DEFAULT_REMOTE_NAME);
				cc.setURI(uri.toPrivateString());
				cc.call();

				StoredConfig config = getLocalRepo().getConfig();
				config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,
						Constants.MASTER, ConfigConstants.CONFIG_KEY_REMOTE,
						Constants.DEFAULT_REMOTE_NAME);
				config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,
						Constants.MASTER, ConfigConstants.CONFIG_KEY_MERGE,
						Constants.MASTER);
				config.save();

				logInfo("Cloned " + this + " to " + workdir);
			}
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, IStatus.ERROR, e.getMessage(), e));
		}
	}

	private void commit(boolean dir) throws CoreException {
		try {
			Repository local = getLocalRepo();
			Git git = new Git(local);
			String folderPattern = null;
			String filePattern = null;
			if (dir) {
				// add empty dir - http://stackoverflow.com/questions/115983/how-do-i-add-an-empty-directory-to-a-git-repository
				File folder = getLocalFile();
				File gitignoreFile = new File(folder.getPath() + "/" + Constants.DOT_GIT_IGNORE);
				// /<folder>/.gitignore
				gitignoreFile.createNewFile();
				if (isRoot()) { // root
					filePattern = Constants.DOT_GIT_IGNORE;
				} else {
					folderPattern = path.toString().substring(1);
					// <folder>/
					filePattern = folderPattern + "/" + Constants.DOT_GIT_IGNORE;
					// <folder>/.gitignore
				}
			} else {
				// /<folder>/<file>
				IPath f = path.removeLastSegments(1);
				// /<folder>/
				String s = f.toString().substring(1);
				// <folder>/
				folderPattern = s.equals("") ? null : s;
				// /<folder>/<file>
				s = path.toString().substring(1);
				filePattern = s;
			}

			// TODO: folder may already exist, no need to add it again
			AddCommand add = git.add();
			if (folderPattern != null) {
				add.addFilepattern(folderPattern);
			}
			add.addFilepattern(filePattern);
			add.call();

			CommitCommand commit = git.commit();
			commit.setMessage("auto-commit of " + this);
			commit.call();
			logInfo("Auto-commit of " + this + " done.");
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, IStatus.ERROR, e.getMessage(), e));
		}
	}

	@Override
	public void delete(int options, IProgressMonitor monitor) throws CoreException {
		if (isRoot()) {
			try {
				getLocalRepo().close();
			} catch (IOException e) {
				throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, 1, "Unable to close cloned repository before deleting : " + this, e));
			}
		}

		File f = getLocalFile();
		try {
			FileUtils.forceDelete(f);
			rm();
		} catch (FileNotFoundException e) {
			// ignore
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, 1, "Unable to delete a file when deleting : " + this, e));
		}
	}

	@Override
	public IFileInfo fetchInfo(int options, IProgressMonitor monitor) throws CoreException {
		if (!isCloned()) {
			initCloneCommitPush(false, monitor);
		}
		if ((options & EFS.CACHE) == 0) // don't use cache
			pull();

		FileInfo fi = new FileInfo();
		fi.setName(getName());
		File f = getLocalFile();
		fi.setExists(f.exists());
		fi.setDirectory(f.isDirectory());
		// TODO: remote commit time?
		// fi.setLastModified(0);
		return fi;
	}

	@Override
	public IFileStore getChild(String name) {
		return new GitFileStore(uri, path.append(name), authority);
	}

	private CredentialsProvider getCredentialsProvider() {
		return new OrionUserCredentialsProvider(authority, uri);
	}

	// TODO: to field
	public File getLocalFile() {
		IPath p = new Path(getWorkingDir().getPath());
		p = p.append(path);
		return p.toFile();
	}

	public Repository getLocalRepo() throws IOException {
		if (localRepo == null) {
			IPath p = new Path(getWorkingDir().getAbsolutePath()).append(Constants.DOT_GIT);
			// <absolute path to working dir>/.git
			localRepo = new FileRepository(p.toFile());
		}
		return localRepo;
	}

	@Override
	public String getName() {
		return path.lastSegment() != null ? path.lastSegment() : "" /* root */;
	}

	@Override
	public IFileStore getParent() {
		if (isRoot())
			return null;
		return new GitFileStore(uri, path.removeLastSegments(1), authority);
	}

	// TODO: to field
	private File getWorkingDir() {
		IPath location = Activator.getDefault().getPlatformLocation();
		// <workspace path>
		if (location == null)
			throw new RuntimeException("Unable to compute local file system location"); //$NON-NLS-1$;

		// TODO: just for now
		location = location.append("PRIVATE_REPO").append(authority);
		if (uri.getScheme() != null)
			location = location.append(uri.getScheme()); // TODO: assume file:/?
		if (uri.getHost() != null)
			location = location.append(uri.getHost());
		location = location.append(uri.getPath());
		// <workspace path>/PRIVATE_REPO/<username>/<protocol>/<host>/<path>/<repo>
		return location.toFile();
	}

	private boolean initBare() throws URISyntaxException, IOException {
		String scheme = uri.getScheme();
		String path = uri.getPath();
		if (scheme != null && !scheme.equals("file")) {
			throw new IllegalArgumentException("#canInit() has mistaken, this is not a local file system URL");
		}
		File sharedRepo = new File(path);
		// remember, we know how to init only local repositories
		if (sharedRepo.exists() && RepositoryCache.FileKey.isGitRepository(new File(sharedRepo, Constants.DOT_GIT), FS.DETECTED)) {
			// nothing to init, a repository already exists at the given location
			return false;
		}

		sharedRepo.mkdir();
		logInfo("Initializing bare repository for " + this);
		FileRepository repository = new FileRepository(new File(sharedRepo, Constants.DOT_GIT));
		repository.create(true);
		return true;
	}

	/**
	 * Clones from shared repo over local transport, creates the repo when
	 * necessary and inits it by pushing a dummy change (.gitignore) file to
	 * remote
	 */
	private void initCloneCommitPush(boolean init, IProgressMonitor monitor) throws CoreException {
		boolean inited = false;
		// if it's a local repository try to init it first
		if (init && canInit()) {
			try {
				inited = initBare();
			} catch (Exception e) {
				throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, 1, "Could not init local bare repo: " + this, e));
			}
		}
		clone(monitor);
		if (inited) {
			commit(true);
			push();
		}
	}

	public boolean isCloned() {
		return getWorkingDir().exists() && RepositoryCache.FileKey.isGitRepository(new File(getWorkingDir(), Constants.DOT_GIT), FS.DETECTED);
	}

	private void logInfo(String message) {
		LoggerFactory.getLogger(GitFileStore.class).info(message);
	}

	public boolean isRoot() {
		return path.isRoot();
	}

	@Override
	public IFileStore mkdir(int options, IProgressMonitor monitor) throws CoreException {
		boolean deep = (options & EFS.SHALLOW) == 0;
		if (isRoot()) {
			initCloneCommitPush(true, monitor);
		} else {
			File f = getLocalFile();
			if (f.getParentFile().exists() && !f.getParentFile().isDirectory()) {
				throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, 1, "Local parent is a file: " + f, null));
			}
			if (deep) {
				GitFileStore root = (GitFileStore) Utils.getRoot(this);
				root.initCloneCommitPush(true, monitor);
				f.mkdirs();
			} else {
				// TODO: sync with remote first
				if (f.getParentFile().exists()) {
					f.mkdir();
				} else {
					throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, 1, "Local parent does not exist: " + f, null));
				}
			}
			commit(true);
			push();
		}
		return this;
	}

	@Override
	public InputStream openInputStream(int options, IProgressMonitor monitor) throws CoreException {
		pull();
		try {
			return new FileInputStream(getLocalFile());
		} catch (FileNotFoundException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, 1, "I/O exception while opening input stream for: " + getLocalFile(), null));
		}
	}

	public OutputStream openOutputStream(final int options, IProgressMonitor monitor) throws CoreException {
		File f = getLocalFile();
		if (!f.getParentFile().exists()) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, 1, "Local parent does not exist: " + f, null));
		}

		return new ByteArrayOutputStream() {
			public void close() throws IOException {
				super.close();
				setContents(toByteArray(), (options & EFS.APPEND) != 0);
			}
		};
	}

	/**
	 * pulls from the remote
	 * 
	 * @throws CoreException
	 */
	private void pull() throws CoreException {
		Transport transport = null;
		try {
			Repository repo = getLocalRepo();
			Git git = new Git(repo);
			PullCommand pull = git.pull();
			pull.setCredentialsProvider(getCredentialsProvider());
			PullResult pullResult = pull.call();
			logInfo("Pull (fetch/merge) result " + pullResult.getFetchResult().getMessages() + "/" + pullResult.getMergeResult().getMergeStatus() + " for " + this);
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, IStatus.ERROR, e.getMessage(), e));
		} finally {
			if (transport != null)
				transport.close();
		}
	}

	private void push() throws CoreException {
		try {
			Repository local = getLocalRepo();
			Git git = new Git(local);

			PushCommand push = git.push();
			push.setRefSpecs(new RefSpec("refs/heads/*:refs/heads/*"));
			push.setCredentialsProvider(getCredentialsProvider());

			Iterable<PushResult> pushResults = push.call();

			for (PushResult pushResult : pushResults) {
				Collection<RemoteRefUpdate> updates = pushResult.getRemoteUpdates();
				for (RemoteRefUpdate update : updates) {
					org.eclipse.jgit.transport.RemoteRefUpdate.Status status = update.getStatus();
					if (status.equals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK) || status.equals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE)) {
						logInfo("Push succeed: " + this);
					} else {
						throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, IStatus.ERROR, status.toString(), null));
					}
				}
			}
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, IStatus.ERROR, e.getMessage(), e));
		}
	}

	private void rm() throws CoreException {
		// TODO: use org.eclipse.jgit.api.RmCommand, see bug 336609
		if (!isRoot()) {
			try {
				Repository local = getLocalRepo();
				Git git = new Git(local);
				CommitCommand commit = git.commit();
				commit.setAll(true);
				commit.setMessage("auto-commit of " + toString());
				commit.call();
			} catch (Exception e) {
				throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT, IStatus.ERROR, e.getMessage(), e));
			}
			push();
		} // else {cannot commit/push root removal}
	}

	void setContents(byte[] bytes, boolean append) throws IOException {
		File f = getLocalFile();
		try {
			byte[] contents = bytes;

			if (append) {
				byte[] oldContents;
				if (f.exists()) {
					FileInputStream fis = new FileInputStream(f);
					oldContents = readBytes(fis);
					fis.close();
				} else {
					oldContents = new byte[0];
				}

				byte[] newContents = new byte[oldContents.length + bytes.length];
				System.arraycopy(oldContents, 0, newContents, 0, oldContents.length);
				System.arraycopy(bytes, 0, newContents, oldContents.length, bytes.length);
				contents = newContents;
			}

			FileOutputStream fos = new FileOutputStream(f);
			fos.write(contents);
			fos.close();

			commit(false);
			pull();
			push();
		} catch (CoreException e) {
			throw new IOException(e.getMessage());
		}
	}

	@Override
	public URI toURI() {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://");
		sb.append(authority);
		sb.append("/");
		String s = uri.toString();
		try {
			sb.append(URLEncoder.encode(s, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			sb.append(URLEncoder.encode(s));
		}
		return URI.create(sb.toString());
	}
}
