/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.server.git.patch.ApplyCommand;
import org.eclipse.orion.server.git.patch.ApplyResult;
import org.eclipse.orion.server.git.patch.PatchApplyException;
import org.eclipse.orion.server.git.patch.PatchFormatException;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.eclipse.orion.server.tests.servlets.git.AllGitTests;
import org.junit.Before;
import org.junit.Test;

public class ApplyCommandTest {

	private Repository db;

	private RawText a;

	private RawText b;

	private ApplyResult init(final String name) throws Exception {
		return init(name, true, true);
	}

	private ApplyResult init(final String name, final boolean preExists, final boolean postExists) throws Exception {
		Git git = new Git(db);

		if (preExists) {
			a = new RawText(readFile(name + "_PreImage"));
			write(new File(db.getDirectory().getParent(), name), a.getString(0, a.size(), false));

			git.add().addFilepattern(name).call();
			git.commit().setMessage("PreImage").call();
		}

		if (postExists)
			b = new RawText(readFile(name + "_PostImage"));

		return new ApplyCommand(db).setPatch(openStream(name + ".patch")).call();
	}

	@Test
	public void testAddA1() throws Exception {
		ApplyResult result = init("A1", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "A1"), result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "A1"), b.getString(0, b.size(), false));
	}

	@Test
	public void testAddA2() throws Exception {
		ApplyResult result = init("A2", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "A2"), result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "A2"), b.getString(0, b.size(), false));
	}

	@Test
	public void testDeleteD() throws Exception {
		ApplyResult result = init("D", true, false);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "D"), result.getUpdatedFiles().get(0));
		assertFalse(new File(db.getWorkTree(), "D").exists());
	}

	@Test(expected = PatchFormatException.class)
	public void testFailureF1() throws Exception {
		init("F1", true, false);
	}

	@Test(expected = PatchApplyException.class)
	public void testFailureF2() throws Exception {
		init("F2", true, false);
	}

	@Test
	public void testModifyE() throws Exception {
		ApplyResult result = init("E");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "E"), result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "E"), b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyX() throws Exception {
		ApplyResult result = init("X");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "X"), result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "X"), b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyY() throws Exception {
		ApplyResult result = init("Y");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "Y"), result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "Y"), b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyZ() throws Exception {
		ApplyResult result = init("Z");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "Z"), result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "Z"), b.getString(0, b.size(), false));
	}

	private InputStream openStream(final String patchFile) throws IOException {
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry("testData/patchTest/" + patchFile);
		return entry.openStream();
	}

	private byte[] readFile(final String patchFile) throws IOException {
		InputStream in = openStream(patchFile);
		if (in == null) {
			fail("No " + patchFile + " test vector");
			return null; // Never happens
		}
		try {
			final byte[] buf = new byte[1024];
			final ByteArrayOutputStream temp = new ByteArrayOutputStream();
			int n;
			while ((n = in.read(buf)) > 0)
				temp.write(buf, 0, n);
			return temp.toByteArray();
		} finally {
			in.close();
		}
	}

	@Before
	public void setUp() throws Exception {
		IPath randomLocation = AllGitTests.getRandomLocation();
		randomLocation = randomLocation.addTrailingSeparator().append(Constants.DOT_GIT);
		File dotGitDir = randomLocation.toFile().getCanonicalFile();
		db = new FileRepository(dotGitDir);
		assertFalse(dotGitDir.exists());
		db.create(false /* non bare */);
	}

	// --- org.eclipse.jgit.lib.RepositoryTestCase

	protected static void checkFile(File f, final String checkData) throws IOException {
		Reader r = new InputStreamReader(new FileInputStream(f), "ISO-8859-1");
		try {
			char[] data = new char[(int) f.length()];
			if (f.length() != r.read(data))
				throw new IOException("Internal error reading file data from " + f);
			assertEquals(checkData, new String(data));
		} finally {
			r.close();
		}
	}

	// --- org.eclipse.jgit.junit.JGitTestUtil

	public static void write(final File f, final String body) throws IOException {
		FileUtils.mkdirs(f.getParentFile(), true);
		Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
		try {
			w.write(body);
		} finally {
			w.close();
		}
	}
}