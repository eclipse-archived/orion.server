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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.orion.server.filesystem.git.GitFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ConcurrentModificationsTest {

	private IPath repositoryPath;

	@Before
	public void before() {
		repositoryPath = getRandomLocation();
	}

	@After
	public void removeSharedRepo() {
		FileSystemHelper.clear(repositoryPath.toFile());
	}

	@Ignore
	public void cloningSameRepo() throws CoreException, IOException {
		// init shared repo
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test1/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		// make private clone 1
		URI uri1 = URI.create(sb.toString());

		IFileStore store1 = EFS.getStore(uri1);
		store1.mkdir(EFS.NONE, null);

		sb.setLength(0);
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test2/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		// make private clone 2
		URI uri2 = URI.create(sb.toString());
		GitFileStore store2 = (GitFileStore) EFS.getStore(uri2);
		store2.mkdir(EFS.NONE, null);
		RepositoryState repoState2 = store2.getLocalRepo().getRepositoryState();
		assertEquals(RepositoryState.SAFE, repoState2);
	}

	@Test
	public void pullingFileChanges() throws CoreException, IOException {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test1/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		// make private clone 1
		URI uri1 = URI.create(sb.toString());
		IFileStore store1 = EFS.getStore(uri1);
		store1.mkdir(EFS.NONE, null);

		sb.setLength(0);
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test2/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		// make private clone 1
		URI uri2 = URI.create(sb.toString());
		IFileStore store2 = EFS.getStore(uri2);
		store2.mkdir(EFS.NONE, null);

		// modify file in clone 1
		IFileStore fileStore1 = store1.getChild("file.txt");
		OutputStream out = fileStore1.openOutputStream(EFS.NONE, null);
		out.write(1);
		out.close();

		// open file in clone 2
		IFileStore fileStore2 = store2.getChild("file.txt");
		InputStream in = fileStore2.openInputStream(EFS.NONE, null);
		assertEquals("1.0", 1, in.read());
		in.close();
	}

	@Test
	public void pullingFolderChanges() throws CoreException, IOException {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test1/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		// make private clone 1
		URI uri1 = URI.create(sb.toString());
		IFileStore store1 = EFS.getStore(uri1);
		store1.mkdir(EFS.NONE, null);

		sb.setLength(0);
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test2/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		// make private clone 1
		URI uri2 = URI.create(sb.toString());
		IFileStore store2 = EFS.getStore(uri2);
		store2.mkdir(EFS.NONE, null);

		// create folder in clone 1
		IFileStore folderStore1 = store1.getChild("folder");
		folderStore1.mkdir(EFS.NONE, null);

		// check folder in clone 2
		IFileStore folderStore2 = store2.getChild("folder");
		final IFileInfo info = folderStore2.fetchInfo();
		assertTrue("1.0", info.exists());
		assertTrue("1.1", info.isDirectory());
	}

	@Test
	public void pushingSeparateLinesFileChanges() throws IOException,
			CoreException {
		concurrentChanges("[0]\n[1]\n[2]\n".getBytes(),
				"[x]\n[1]\n[2]\n".getBytes(), "[0]\n[1]\n[y]\n".getBytes(),
				"[x]\n[1]\n[y]\n".getBytes());
	}

	@Test
	public void gitMergeAddsLineSeparator() throws IOException, CoreException {
		concurrentChanges("[0]\n[1]\n[2]".getBytes(),
				"[x]\n[1]\n[2]".getBytes(), "[0]\n[1]\n[y]".getBytes(),
				"[x]\n[1]\n[y]".getBytes());
	}

	@Test
	public void pushingSameLineFileChanges() throws IOException, CoreException {
		concurrentChanges(new byte[] { 1 }, new byte[] { 2 }, new byte[] { 3 },
				new byte[] { 4 }/* anything, will fail anyway */);
	}

	private IPath getRandomLocation() {
		return FileSystemHelper
				.getRandomLocation(FileSystemHelper.getTempDir());
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

	private void concurrentChanges(byte[] base, byte[] left, byte[] right,
			byte[] merge) throws CoreException, IOException {
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test1/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		// make private clone 1
		URI uri1 = URI.create(sb.toString());
		IFileStore store1 = EFS.getStore(uri1);
		store1.mkdir(EFS.NONE, null);

		sb.setLength(0);
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test2/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		// make private clone 1
		URI uri2 = URI.create(sb.toString());
		IFileStore store2 = EFS.getStore(uri2);
		store2.mkdir(EFS.NONE, null);

		// create file in clone 1
		IFileStore fileStore1 = store1.getChild("file.txt");
		OutputStream out = fileStore1.openOutputStream(EFS.NONE, null);
		out.write(base);
		out.close();

		// check the file in clone 2
		IFileStore fileStore2 = store2.getChild("file.txt");
		final IFileInfo info = fileStore2.fetchInfo();
		assertTrue("1.0", info.exists());
		assertTrue("1.1", !info.isDirectory());
		InputStream in = fileStore2.openInputStream(EFS.NONE, null);
		byte[] actual = readBytes(in);
		assertEquals("1.2", base.length, actual.length);
		assertEquals("1.3", new String(base), new String(actual));
		in.close();

		// modify the file in clone 1
		fileStore1 = store1.getChild("file.txt");
		out = fileStore1.openOutputStream(EFS.NONE, null);
		out.write(left);
		out.close();

		// modify the same file in clone 2
		fileStore2 = store2.getChild("file.txt");
		out = fileStore2.openOutputStream(EFS.NONE, null);
		out.write(right);
		out.close();

		// check the file in clone 2
		in = fileStore2.openInputStream(EFS.NONE, null);
		actual = readBytes(in);
		assertEquals("1.4", merge.length, actual.length);
		assertEquals("1.5", new String(merge), new String(actual));
		in.close();

		// now check the file in clone 1
		in = fileStore1.openInputStream(EFS.NONE, null);
		actual = readBytes(in);
		assertEquals("1.6", merge.length, actual.length);
		assertEquals("1.7", new String(merge), new String(actual));
		in.close();
	}
}
