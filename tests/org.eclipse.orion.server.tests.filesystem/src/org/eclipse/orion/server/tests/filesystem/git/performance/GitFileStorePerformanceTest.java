/*******************************************************************************
 *  Copyright (c) 2011 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.filesystem.git.performance;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.orion.server.filesystem.git.GitFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.junit.Test;

public class GitFileStorePerformanceTest {

	private GitFileSystem fs = new GitFileSystem();

	@Test
	public void setFileContents() throws CoreException, IOException {
		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this
				.getClass().getName() + '#' + getMethodName() + "()"); //$NON-NLS-1$

		IPath repositoryPath = getRandomLocation();
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(URIUtil.toURI(repositoryPath).toString());
		sb.append("?/");
		URI uri = URI.create(sb.toString());
		GitFileStore root = (GitFileStore) fs.getStore(uri);

		root.mkdir(EFS.NONE, null);
		IFileStore folder1 = root.getChild("folder1"); //$NON-NLS-1$
		folder1.mkdir(EFS.NONE, null);
		IFileStore file0 = folder1.getChild("file0.txt"); //$NON-NLS-1$
		OutputStream out = file0.openOutputStream(EFS.NONE, null);
		out.write("file0.txt content".getBytes()); //$NON-NLS-1$
		out.close();

		try {
			for (int i = 0; i < 100; i++) {

				perfMeter.start();

				// modify a file, overwrite content
				long now = System.currentTimeMillis();
				out = file0.openOutputStream(EFS.NONE, null);
				out.write(Long.toString(now).getBytes());
				out.close();
				// append
				out = file0.openOutputStream(EFS.APPEND, null);
				out.write(Long.toString(now).getBytes());
				out.close();

				perfMeter.stop();

				// verify content
				InputStream in = file0.openInputStream(EFS.NONE, null);
				byte[] actual = readBytes(in);
				in.close();
				assertEquals(Long.toString(now) + Long.toString(now),
						new String(actual));
			}

			perfMeter.commit();
			perf.assertPerformance(perfMeter);
		} finally {
			perfMeter.dispose();
		}
	}

	protected static byte[] readBytes(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int r;
		byte[] data = new byte[16384];
		while ((r = is.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, r);
		}
		buffer.flush();
		return buffer.toByteArray();
	}

	protected IPath getRandomLocation() {
		return FileSystemHelper
				.getRandomLocation(FileSystemHelper.getTempDir());
	}

	private static String getMethodName() {
		final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		return ste[3].getMethodName();
	}

}
