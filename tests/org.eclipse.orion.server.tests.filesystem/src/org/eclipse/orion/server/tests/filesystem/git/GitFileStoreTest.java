/*******************************************************************************
 *  Copyright (c) 2010 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.filesystem.git;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.CoreTest;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.filesystem.git.Utils;
import org.eclipse.orion.server.filesystem.git.GitFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;
import org.junit.*;

public class GitFileStoreTest {

	private IPath repositoryPath;
	GitFileSystem fs = new GitFileSystem();

	@Before
	public void before() {
		repositoryPath = getRandomLocation();
	}

	@After
	public void removeSharedRepo() {
		FileSystemHelper.clear(repositoryPath.toFile());
	}

	@Test
	public void cloneRepoOnDeepFolderCreation() throws IOException,
			CoreException {
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s
				+ "?/folder/subfolder");
		IFileStore store = fs.getStore(uri);
		store.mkdir(EFS.NONE, null);

		IFileInfo info = store.fetchInfo();
		assertTrue("1.1", info.exists());
		assertTrue("1.2", info.isDirectory());

		GitFileStore gitStore = (GitFileStore) store;
		RepositoryState state = gitStore.getLocalRepo().getRepositoryState();
		assertTrue("1.3", RepositoryState.SAFE.equals(state));
	}

	@Test
	public void fetchInfoForInitedSharedRepository() throws IOException {
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s + "?/");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedExists(gfs, false);

		IFileInfo info = gfs.fetchInfo();
		assertTrue("1.1", info.exists());
		assertTrue("1.2", info.isDirectory());
	}

	@Test(expected = JGitInternalException.class)
	public void fetchInfoForAnEmptySharedRepository() throws IOException {
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s + "?/");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedExists(gfs, true);

		IFileInfo info = gfs.fetchInfo();
		assertTrue("1.1", info.exists());
		assertTrue("1.2", info.isDirectory());
	}

	@Test
	public void fetchInfoWhenSharedRepositoryDoesntExist() {
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s + "?/");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedDoesNotExist(gfs);

		IFileInfo info = gfs.fetchInfo();
		assertFalse("1.1", info.exists());
	}

	@Test(expected = CoreException.class)
	public void getChildNamesForInitedSharedRepository() throws CoreException {
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s + "?/");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedExists(gfs, false);

		String[] childNames = gfs.childNames(EFS.NONE, null);
	}

	@Test(expected = JGitInternalException.class)
	public void getChildNamesForAnEmptySharedRepository() throws CoreException {
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s + "?/");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedExists(gfs, true);
		gfs.mkdir(EFS.NONE, null);

		String[] childNames = gfs.childNames(EFS.NONE, null);
	}

	@Test(expected = CoreException.class)
	public void getChildNamesWhenSharedRepositoryDoesntExist()
			throws CoreException {
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s + "?/");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);

		ensureSharedDoesNotExist(gfs);

		String[] childNames = gfs.childNames(EFS.NONE, null);
	}

	@Test
	public void folderForSharedRepoAlreadyExist() throws CoreException {
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s + "?/");
		IFileStore store = fs.getStore(uri);
		repositoryPath.toFile().mkdir();
		store.mkdir(EFS.NONE, null);

		IFileInfo info = store.fetchInfo();
		assertTrue("1.1", info.exists());
		assertTrue("1.2", info.isDirectory());
	}

	@Test
	public void folderForPrivateRepoAlreadyExist() throws CoreException {
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s + "?/");
		GitFileStore store = (GitFileStore) fs.getStore(uri);
		File privateRepo = store.getLocalFile();
		assertFalse(privateRepo.exists());
		privateRepo.mkdir();
		assertTrue(privateRepo.isDirectory());
		store.mkdir(EFS.NONE, null);

		IFileInfo info = store.fetchInfo();
		assertTrue("1.1", info.exists());
		assertTrue("1.2", info.isDirectory());
	}

	private IPath getRandomLocation() {
		return FileSystemHelper
				.getRandomLocation(FileSystemHelper.getTempDir());
	}

	private void ensureSharedExists(GitFileStore root, boolean empty) {
		try {
			if (empty) {
				String path = decodeLocalPath(root.getUrl().toString());
				File sharedRepo = new File(path);
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

	private void ensureSharedDoesNotExist(GitFileStore gfs) {
		removeSharedRepo();
	}

	/**
	 * @param url
	 * @see org.eclipse.orion.server.filesystem.git.GitFileStore#initBare()
	 */
	void initBare(URL url) throws IOException {
		String path = decodeLocalPath(url.toString());
		File sharedRepo = new File(path);
		if (!sharedRepo.exists()) {
			sharedRepo.mkdir();
			FileRepository repository = new FileRepository(new File(sharedRepo,
					Constants.DOT_GIT));
			repository.create(true);
		}
	}

	// org.eclipse.orion.server.filesystem.git.GitFileStore.decodeLocalPath(String)
	private static String decodeLocalPath(final String s) {
		String r = new String(s);
		r = r.substring(0, r.lastIndexOf('?'));
		r = r.replace('+', ' ');
		return r;
	}

}
