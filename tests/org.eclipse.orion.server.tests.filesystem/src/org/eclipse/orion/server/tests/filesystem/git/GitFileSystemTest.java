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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.*;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.internal.filesystem.NullFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;
import org.junit.Test;


public class GitFileSystemTest {
	
	GitFileSystem fs = new GitFileSystem();
	
	@Test
	public void noQueryInString() throws MalformedURLException,
			URISyntaxException {
		URI uri = new URI(GitFileSystem.SCHEME_GIT + "://test/"
				+ "git://host.com/repository.git");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		assertEquals("", gfs.getName());
		assertTrue(gfs.isRoot());
		File f = gfs.getLocalFile();
		assertEquals("", f.getName());
	}

	@Test(expected = IllegalArgumentException.class)
	public void emptyQueryInString() throws MalformedURLException,
			URISyntaxException {
		URI uri = new URI(GitFileSystem.SCHEME_GIT + "://test/"
				+ "git://host.com/repository.git?");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		File f = gfs.getLocalFile();
		assertEquals("", f.getName());
	}
	
	@Test
	public void rootFromString() throws MalformedURLException,
			URISyntaxException {
		URI uri = new URI(GitFileSystem.SCHEME_GIT + "://test/"
				+ "git://host.com/repository.git?/");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		assertEquals("", gfs.getName());
		assertTrue(gfs.isRoot());
		File f = gfs.getLocalFile();
		assertEquals("repository.git", f.getName());
	}

	@Test
	public void topLevelFolderFromString() throws MalformedURLException,
			URISyntaxException {
		URI uri = new URI(GitFileSystem.SCHEME_GIT + "://test/"
				+ "git://host.com/repository.git?/project");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		assertEquals("project", gfs.getName());
		File f = gfs.getLocalFile();
		// assertTrue(f.isDirectory()); doesn't exists, not cloned
		assertEquals("project", f.getName());
	}

	@Test
	public void subfolderFromString() throws MalformedURLException,
			URISyntaxException {
		URI uri = new URI(GitFileSystem.SCHEME_GIT + "://test/"
				+ "git://host.com/repository.git?/project/folder/");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		assertEquals("folder", gfs.getName());
		File f = gfs.getLocalFile();
		// assertTrue(f.isDirectory()); doesn't exists, not cloned
		assertEquals("folder", f.getName());
	}

	@Test
	public void fileFromString() throws MalformedURLException,
			URISyntaxException {
		URI uri = new URI(GitFileSystem.SCHEME_GIT + "://test/"
				+ "git://host.com/repository.git?/project/folder/file.txt");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		assertEquals("file.txt", gfs.getName());
		File f = gfs.getLocalFile();
		// assertTrue(f.isFile()); doesn't exists, not cloned
		assertEquals("file.txt", f.getName());
	}
	
	@Test
	public void noGitfsSchemeInUri() throws URISyntaxException {
		// no gitfs scheme, no authority
		URI uri = new URI("git://host.com/repository.git");
		IFileStore s = fs.getStore(uri);
		assertFalse(s instanceof GitFileStore);
		assertTrue(s instanceof NullFileStore);
	}

	@Test
	public void allParamsInUriForGitTransport() throws URISyntaxException {
		URI uri = new URI(GitFileSystem.SCHEME_GIT, "test",
				"git://host.com/repository.git", "/", null/* fragment */);
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		assertEquals("", gfs.getName());
		assertTrue(gfs.isRoot());
		File f = gfs.getLocalFile();
		assertEquals("repository.git", f.getName());
	}

	@Test
	public void allParamsInUriForLocalTransport() throws URISyntaxException {
		URI uri = new URI(GitFileSystem.SCHEME_GIT, "test",
				"c:/temp", "/", null/* fragment */);
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		assertEquals("", gfs.getName());
		assertTrue(gfs.isRoot());
		File f = gfs.getLocalFile();
		assertEquals("repository.git", f.getName());
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void nullAuthorityInUri() throws URISyntaxException {
		URI uri = new URI(GitFileSystem.SCHEME_GIT, null /* authority */,
				"git://host.com/repository.git", "/", null/* fragment */);
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		assertEquals("", gfs.getName());
		assertTrue(gfs.isRoot());
		File f = gfs.getLocalFile();
		assertEquals("repository.git", f.getName());
	}
}
