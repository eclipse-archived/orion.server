/*******************************************************************************
 *  Copyright (c) 2010, 2011 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.filesystem.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.tests.harness.CoreTest;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.server.filesystem.git.GitFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GitFileStoreTest {

	/**
	 * Path to the "shared" repository.
	 */
	private IPath repositoryPath;
	GitFileSystem fs = new GitFileSystem();
	private GitFileStore gfs;

	@Before
	public void before() {
		repositoryPath = getRandomLocation();
	}

	@After
	public void removeSharedRepo() throws IOException {
		gfs.getLocalRepo().close();
		if (repositoryPath.toFile().exists())
			FileUtils.delete(repositoryPath.toFile(), FileUtils.RECURSIVE);
	}

	@Test
	public void cloneRepoOnDeepFolderCreation() throws IOException,
			CoreException {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/folder/subfolder");
		URI uri = URI.create(sb.toString());
		IFileStore store = fs.getStore(uri);
		store.mkdir(EFS.NONE, null);

		IFileInfo info = store.fetchInfo();
		assertTrue("1.1", info.exists());
		assertTrue("1.2", info.isDirectory());

		gfs = (GitFileStore) store;
		RepositoryState state = gfs.getLocalRepo().getRepositoryState();
		assertTrue("1.3", RepositoryState.SAFE.equals(state));
	}

	@Test
	public void fetchInfoForInitedSharedRepository() {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		URI uri = URI.create(sb.toString());
		gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedExists(gfs, false);

		IFileInfo info = gfs.fetchInfo();
		assertTrue("1.1", info.exists());
		assertTrue("1.2", info.isDirectory());
	}

	@Test
	public void fetchInfoForAnEmptySharedRepository() {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		URI uri = URI.create(sb.toString());
		gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedExists(gfs, true);

		IFileInfo info = gfs.fetchInfo();
		assertFalse("1.1", info.exists());
		assertFalse("1.2", info.isDirectory());
	}

	@Test
	public void fetchInfoWhenSharedRepositoryDoesntExist() {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		URI uri = URI.create(sb.toString());
		gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedDoesNotExist();

		IFileInfo info = gfs.fetchInfo();
		assertFalse("1.1", info.exists());
	}

	@Test(expected = CoreException.class)
	public void getChildNamesForInitedSharedRepository() throws CoreException {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		URI uri = URI.create(sb.toString());
		gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedExists(gfs, false);

		gfs.childNames(EFS.NONE, null);
	}

	@Test(expected = CoreException.class)
	public void getChildNamesForAnEmptySharedRepository() throws CoreException {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		URI uri = URI.create(sb.toString());
		gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedExists(gfs, true);
		gfs.mkdir(EFS.NONE, null);

		String[] childNames = gfs.childNames(EFS.NONE, null);
		String[] expectedNames = new String[] {Constants.DOT_GIT, Constants.DOT_GIT_IGNORE};
		assertEquals(expectedNames.length, childNames.length);
		assertEquals(expectedNames[0], childNames[0]);
		assertEquals(expectedNames[1], childNames[1]);
	}

	@Test(expected = CoreException.class)
	public void getChildNamesWhenSharedRepositoryDoesntExist()
			throws CoreException {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		URI uri = URI.create(sb.toString());
		gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedDoesNotExist();

		gfs.childNames(EFS.NONE, null);
	}

	@Test
	public void folderForSharedRepoAlreadyExist() throws CoreException {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		URI uri = URI.create(sb.toString());
		IFileStore store = fs.getStore(uri);
		gfs = (GitFileStore) store;
		assertFalse(repositoryPath.toFile().exists());
		// create an empty dir, where the "shared" repository should be
		repositoryPath.toFile().mkdir();
		assertTrue(repositoryPath.toFile().isDirectory());
		store.mkdir(EFS.NONE, null);

		IFileInfo info = store.fetchInfo();
		assertTrue("1.1", info.exists());
		assertTrue("1.2", info.isDirectory());
	}

	@Test
	public void folderForPrivateRepoAlreadyExist() throws CoreException {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		URI uri = URI.create(sb.toString());
		gfs = (GitFileStore) fs.getStore(uri);
		File privateRepo = gfs.getLocalFile();
		assertFalse(repositoryPath.toFile().exists());
		assertFalse(privateRepo.exists());
		// create an empty dir, where the "private" repository should be
		privateRepo.mkdir();
		assertTrue(privateRepo.isDirectory());
		gfs.mkdir(EFS.NONE, null);

		IFileInfo info = gfs.fetchInfo();
		assertTrue("1.1", info.exists());
		assertTrue("1.2", info.isDirectory());
	}

	@Test
	public void toStringRevealsPassword() throws URISyntaxException {
		final String PASSWORD = "passw0rd";

		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append("ssh://");
		sb.append("user");
		sb.append(":");
		sb.append(PASSWORD);
		sb.append("@");
		sb.append("localhost/git/test.git");
		sb.append("?/");
		URI uri = new URI(sb.toString());
		IFileStore store = fs.getStore(uri);
		gfs = (GitFileStore) store;
		String toString = store.toString();

		assertFalse(toString.contains(PASSWORD));
	}

	private IPath getRandomLocation() {
		return FileSystemHelper
				.getRandomLocation(FileSystemHelper.getTempDir());
	}

	private void ensureSharedExists(GitFileStore root, boolean empty) {
		try {
			if (empty) {
				String path = root.toURI().getPath();
				path = URLDecoder.decode(path, "UTF-8");
				path = path.substring("/file://".length());
				IPath p = new Path(path);
				File sharedRepo = p.toFile();
				// TODO: can init only local repositories
				// checking if the folder exists may not be enough though
				if (!sharedRepo.exists()) {
					sharedRepo.mkdir();
					FileRepository repository = new FileRepository(new File(
							sharedRepo, Constants.DOT_GIT));
					repository.create(true);
				}
			} else {
				// clone to private space:
				// mkdir will init the shared repo with .gitignore file, see
				// GitFileStore.initCloneCommitPush(IProgressMonitor)
				root.mkdir(EFS.NONE, null);
				// remove private clone
				FileSystemHelper.clear(root.getLocalFile());
			}
			assertFalse(root.isCloned());
		} catch (IOException e) {
			CoreTest.fail("ensureExists", e);
		} catch (CoreException e) {
			CoreTest.fail("ensureExists", e);
		}
	}

	private void ensureSharedDoesNotExist() {
		try {
			removeSharedRepo();
		} catch (IOException e) {
			Assert.fail();
		}
		assertFalse(repositoryPath.toFile().exists());
	}

}
