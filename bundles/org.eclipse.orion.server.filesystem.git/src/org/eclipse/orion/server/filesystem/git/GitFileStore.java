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

import org.eclipse.orion.internal.server.filesystem.git.Activator;
import org.eclipse.orion.internal.server.filesystem.git.Utils;

import org.eclipse.orion.server.core.LogHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.filesystem.provider.FileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.core.op.CloneOperation;
import org.eclipse.egit.core.op.PushOperation;
import org.eclipse.egit.core.op.PushOperationResult;
import org.eclipse.egit.core.op.PushOperationSpecification;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectIdRef.PeeledNonTag;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;

public class GitFileStore extends FileStore {

	private Repository localRepo;
	
	private URL gitUrl;
	private String authority;

	public GitFileStore(String s, String authority) {
		try {
			gitUrl = new URL(null, s, new URLStreamHandler() {

				@Override
				protected URLConnection openConnection(URL u)
						throws IOException {
					// never called, see #openInputStream(int, IProgressMonitor)
					return null;
				}
			});
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
		
		if (authority == null)
			throw new IllegalArgumentException("authority cannot be null");
		this.authority = authority;
		if (gitUrl.getQuery() == null)
			throw new IllegalArgumentException("missing query");
	}

	public URL getUrl() {
		return gitUrl;
	}
	
	// TODO: to field
	private File getWorkingDir() {
		IPath location = Activator.getDefault().getPlatformLocation();
		// <workspace path>
		if (location == null)
			throw new RuntimeException(
					"Unable to compute local file system location"); //$NON-NLS-1$;

		// TODO: just for now
		location = location.append("PRIVATE_REPO").append(authority)
				.append(gitUrl.getProtocol()).append(gitUrl.getHost()).append(gitUrl.getPath());
		// <workspace path>/PRIVATE_REPO/<username>/<protocol>/<host>/<path>/<repo>
		return location.toFile();
	}

	// TODO: to field
	public File getLocalFile() {
		String q = gitUrl.getQuery();
		String p = getWorkingDir().getAbsolutePath() + "/" + q;
		return new File(p);
	}

	public Repository getLocalRepo() throws IOException {
		if (localRepo == null) {
			IPath p = new Path(getWorkingDir().getAbsolutePath())
					.append(Constants.DOT_GIT);
			// <absolute path to working dir>/.git
			localRepo = new FileRepository(p.toFile());
		}
		return localRepo;
	}

	@Override
	public String[] childNames(int options, IProgressMonitor monitor)
			throws CoreException {
		if (!Utils.isValidRemote(this)) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					1, this + " doesn't point to a valid repository."
							+ getLocalFile(), null));
		}
		if (!isCloned()) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					1, "A private clone for " + this + " doesn't exist."
							+ getLocalFile(), null));
		}
		pull();
		File f = getLocalFile();
		return f.list(null);
	}

	public boolean isCloned() {
		// TODO: naive check
		return getWorkingDir().exists();
	}

	@Override
	public IFileInfo fetchInfo(int options, IProgressMonitor monitor)
			throws CoreException {
		if (Utils.isValidRemote(this)) {
			if (!isCloned()) {
				initCloneCommitPush(monitor);
			}
			pull();
		}
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
		String s = gitUrl.toString();
		if (s.endsWith("/"))
			s = s + name;
		else
			s = s + "/" + name;
		return new GitFileStore(s, authority);
	}

	@Override
	public String getName() {
		String q = gitUrl.getQuery();
		Path p = new Path(q);
		return p.lastSegment() != null ? p.lastSegment() : "" /* root */;
	}

	@Override
	public IFileStore getParent() {
		String q = gitUrl.getQuery();
		Path p = new Path(q);
		// return null for the root store
		if (p.isRoot())
			return null;
		IPath ip = p.removeLastSegments(1);
		String s = gitUrl.toString();
		s = s.substring(0, s.indexOf(q));
		return new GitFileStore(s + ip.toString(), authority);
	}

	@Override
	public InputStream openInputStream(int options, IProgressMonitor monitor)
			throws CoreException {
		pull();
		try {
			return new FileInputStream(getLocalFile());
		} catch (FileNotFoundException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					1, "I/O exception while opening input stream for: "
							+ getLocalFile(), null));
		}
	}

	@Override
	public URI toURI() {
		return URI.create(GitFileSystem.SCHEME_GIT + ":/"
				+ Utils.encodeLocalPath(gitUrl.toString()));
	}

	@Override
	public IFileStore mkdir(int options, IProgressMonitor monitor)
			throws CoreException {
		boolean deep = (options & EFS.SHALLOW) == 0;
		if (isRoot()) {
			initCloneCommitPush(monitor);
		} else {
			File f = getLocalFile();
			if (f.getParentFile().exists() && !f.getParentFile().isDirectory()) {
				throw new CoreException(new Status(IStatus.ERROR,
						Activator.PI_GIT, 1, "Local parent is a file: " + f,
						null));
			}
			if (deep) {
				GitFileStore root = (GitFileStore) Utils.getRoot(this);
				root.initCloneCommitPush(monitor);
				f.mkdirs();
			} else {
				// TODO: sync with remote first
				if (f.getParentFile().exists()) {
					f.mkdir();
				} else {
					throw new CoreException(new Status(IStatus.ERROR,
							Activator.PI_GIT, 1,
							"Local parent does not exist: " + f, null));
				}
			}
			commit(true);
			push();
		}
		return this;
	}

	public OutputStream openOutputStream(final int options,
			IProgressMonitor monitor) throws CoreException {
		File f = getLocalFile();
		if (!f.getParentFile().exists()) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					1, "Local parent does not exist: " + f, null));
		}

		return new ByteArrayOutputStream() {
			public void close() throws IOException {
				super.close();
				setContents(toByteArray(), (options & EFS.APPEND) != 0);
			}
		};
	}

	void setContents(byte[] bytes, boolean append) throws IOException {
		File f = getLocalFile();
		try {
			byte[] oldContents;
			if (f.exists()) {
				FileInputStream fis = new FileInputStream(f);
				oldContents = readBytes(fis);
				fis.close();
			} else {
				oldContents = new byte[0];
			}

			byte[] contents = bytes;
			if (append) {
				byte[] newContents = new byte[oldContents.length + bytes.length];
				System.arraycopy(oldContents, 0, newContents, 0,
						oldContents.length);
				System.arraycopy(bytes, 0, newContents, oldContents.length,
						bytes.length);
				contents = newContents;
			}

			FileOutputStream fos = new FileOutputStream(f);
			fos.write(contents);
			fos.close();

			commit(false);
			pull();
			push();
		} catch (FileNotFoundException e) {
			throw new IOException(e);
		} catch (CoreException e) {
			throw new IOException(e);
		}
	}
	
	public boolean isRoot() {
		return getUrl().getQuery().equals("/");
	}
	
	@Override
	public void delete(int options, IProgressMonitor monitor)
			throws CoreException {
		if (isRoot()) {
			try {
				getLocalRepo().close();
			} catch (IOException e) {
				throw new CoreException(new Status(IStatus.ERROR,
						Activator.PI_GIT, 1,
						"Unable to close cloned repository before deleting : "
								+ this, e));
			}
		}
			
		File f = getLocalFile();
		try {
			FileUtils.forceDelete(f);
			rm();
		} catch (FileNotFoundException e) {
			// ignore
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					1, "Unable to delete a file when deleting : " + this, e));
		}
	}

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
	
	// TODO use me
	private boolean shouldInit = true;

	/**
	 * Clones from shared repo over local transport, creates the repo when
	 * necessary and inits it by pushing a dummy change (.gitignore) file to
	 * remote
	 */
	void initCloneCommitPush(IProgressMonitor monitor) throws CoreException {
		boolean inited = false;
		if (canInit() && shouldInit) {
			try {
				inited = initBare();
			} catch (Exception e) {
				throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
						1, "Could not init local bare repo: " + this, e));
			}
		}
		clone(monitor);
		if (inited) {
			commit(true);
			push();
		}
	}

	private boolean canInit() {
		// org.eclipse.jgit.transport.TransportLocal.canHandle(URIish, FS)
		try {
			URIish uri = Utils.toURIish(getUrl());
			if (uri.getHost() != null || uri.getPort() > 0 || uri.getUser() != null
					|| uri.getPass() != null || uri.getPath() == null)
				return false;

			if ("file".equals(uri.getScheme()) || uri.getScheme() == null)
				// return fs.resolve(new File("."), uri.getPath()).isDirectory();
				return true;
		} catch (URISyntaxException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_GIT, 1,
					"Cannot init" + this
							+ ". The URL cannot be parsed as a URI reference",
					e));
		}
		return false;
	}

	private boolean canPush() {
		Transport transport = null;
		try {
			URIish remote = Utils.toURIish(getUrl());
			Repository local = getLocalRepo();
			if (!Transport.canHandleProtocol(remote, FS.DETECTED))
				return false;
			transport = Transport.open(local, remote);
			transport.openPush().close();
			return true;
		} catch (Exception e) {
			// ignore
		} finally {
			if (transport != null)
				transport.close();
		}
		return false;
	}
	
	private void clone(IProgressMonitor monitor) throws CoreException {
		try {
			URIish uri = Utils.toURIish(getUrl());
			File workdir = getWorkingDir();
			if (!isCloned()) {
				workdir.mkdirs();
				// TODO: ListRemoteOperation.getRemoteRef
				Ref ref = new PeeledNonTag(Ref.Storage.NETWORK,	"refs/heads/master", null);
				final CloneOperation op = new CloneOperation(uri, true, null,
						workdir, ref, "origin", 0);
				op.run(monitor);
				LogHelper.log(new Status(IStatus.INFO, Activator.PI_GIT, 1,
						"Cloned " + this + " to " + workdir, null));
			}
		} catch (InterruptedException e) {
			// ignore
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					IStatus.ERROR, e.getMessage(), e));
		}
	}

	private boolean initBare() throws URISyntaxException, IOException {
		//	File sharedRepo = new File(gfs.getUrl().toURI());
		String path = decodeLocalPath(getUrl().toString());
		File sharedRepo = new File(path);
		// TODO: can init only local repositories
		// checking if the folder exists may not be enough though
		if (!sharedRepo.exists()) {
			sharedRepo.mkdir();
			LogHelper.log(new Status(IStatus.INFO, Activator.PI_GIT, 1,
					"Initializing bare repository for " + this, null));
			FileRepository repository = new FileRepository(new File(sharedRepo,	Constants.DOT_GIT));
			repository.create(true);	
			return true;
		}
		return false;
	}

	private static String decodeLocalPath(final String s) {
		String r = new String(s);
		r = r.substring(0, r.lastIndexOf('?'));
		r = r.replace('+', ' ');
		return r;
	}
	
	private void push() throws CoreException {
		if (!canPush()) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_GIT, 1,
					"Ignored push request for " + this, null));
			return;
		}
		try {
			Repository local = getLocalRepo();
			
			final List<RefSpec> fetchSpecs = Collections.emptyList();
			List<RefSpec> refSpecs = new ArrayList<RefSpec>();
			refSpecs.add(new RefSpec("refs/heads/*:refs/heads/*"));
			final Collection<RemoteRefUpdate> updates = Transport
					.findRemoteRefUpdatesFor(local, refSpecs, fetchSpecs);

			final PushOperationSpecification spec = new PushOperationSpecification();
			spec.addURIRefUpdates(Utils.toURIish(getUrl()),
					copyUpdates(updates));

			List<RemoteConfig> remoteConfigs = RemoteConfig
					.getAllRemoteConfigs(local.getConfig());

			PushOperation operation = new PushOperation(local, spec, false,
					remoteConfigs.get(0), 0);
			operation.run(null);

			PushOperationResult operationResult = operation.getOperationResult();
			URIish uri = operationResult.getURIs().iterator().next();
			org.eclipse.jgit.transport.RemoteRefUpdate.Status status = operationResult.getPushResult(uri).getRemoteUpdates().iterator()
					.next().getStatus();
			if (status
					.equals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK)
					|| status
							.equals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE)) {
				LogHelper.log(new Status(IStatus.INFO, Activator.PI_GIT, 1,
						"Push succeed: " + this, null));
				return;
			}
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					IStatus.ERROR, status.toString(), null));
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					IStatus.ERROR, e.getMessage(), e));
		}
	}
	
	// org.eclipse.egit.ui.internal.push.ConfirmationPage.copyUpdates(Collection<RemoteRefUpdate>)
	private Collection<RemoteRefUpdate> copyUpdates(
			final Collection<RemoteRefUpdate> refUpdates) throws IOException {
		final Collection<RemoteRefUpdate> copy = new ArrayList<RemoteRefUpdate>(
				refUpdates.size());
		for (final RemoteRefUpdate rru : refUpdates)
			copy.add(new RemoteRefUpdate(rru, null));
		return copy;
	}
	
	/** 
	 * pulls from the remote
	 * @throws CoreException  
	 */
	private void pull() throws CoreException {
		Transport transport = null;
		try {
			Repository repo = getLocalRepo();
			Git git = new Git(repo);
			PullCommand pull = git.pull();
			PullResult pullResult = pull.call();
			LogHelper.log(new Status(IStatus.INFO, Activator.PI_GIT, 1,
					"Pull (fetch/merge) result "
							+ pullResult.getFetchResult().getMessages() + "/"
							+ pullResult.getMergeResult().getMergeStatus()
							+ " for " + this, null));
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					IStatus.ERROR, e.getMessage(), e));
		} catch (GitAPIException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					IStatus.ERROR, e.getMessage(), e));
		} finally {
			if (transport != null)
				transport.close();
		}
	}
	
	private void rm() throws CoreException {
		// TODO: use org.eclipse.jgit.api.RmCommand, see Enhancement 379
		try {
			if (!isRoot()) {
				Repository local = getLocalRepo();
				Git git = new Git(local);
				CommitCommand commit = git.commit();
				commit.setAll(true);
				commit.setMessage("auto-commit of " + toString());
				commit.call();
				push();
			} // else {cannot commit/push root removal}
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					IStatus.ERROR, e.getMessage(), e));
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
				String query = getUrl().getQuery();
				if (query.equals("/")) { // root
					filePattern = Constants.DOT_GIT_IGNORE;
				} else {
					folderPattern = new Path(query).toString().substring(1);
					// <folder>/
					filePattern = folderPattern + "/" + Constants.DOT_GIT_IGNORE;
					// <folder>/.gitignore
				}
			} else {
				// /<folder>/<file>
				IPath f = new Path(getUrl().getQuery()).removeLastSegments(1);
				// /<folder>/
				String s = f.toString().substring(1);
				// <folder>/
				folderPattern = s.equals("") ? null : s;
				// /<folder>/<file>
				s = getUrl().getQuery().substring(1);
				filePattern = s;
			}

			// TODO: folder may already exist, no need to add it again
			if (folderPattern != null) {
				AddCommand addFolder = git.add();
				// add (parent) folder first
				addFolder.addFilepattern(folderPattern);
				addFolder.call();
			}

			AddCommand addFile = git.add();
			addFile.addFilepattern(filePattern);
			addFile.call();

			CommitCommand commit = git.commit();
			commit.setMessage("auto-commit of " + this);
			commit.call();
			LogHelper.log(new Status(IStatus.INFO, Activator.PI_GIT, 1,
					"Auto-commit of " + this + " done.", null));
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_GIT,
					IStatus.ERROR, e.getMessage(), e));		
		}
	}
}
