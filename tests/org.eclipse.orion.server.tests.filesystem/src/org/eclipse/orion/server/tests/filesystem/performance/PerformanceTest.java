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
package org.eclipse.orion.server.tests.filesystem.performance;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.junit.Test;

public abstract class PerformanceTest {
	protected IFileStore root;

	protected abstract void initRoot() throws CoreException;

	protected void init() throws CoreException, IOException {
		initRoot();

		root.mkdir(EFS.NONE, null);
		IFileStore file0 = root.getChild("file0.txt"); //$NON-NLS-1$
		OutputStream out = file0.openOutputStream(EFS.NONE, null);
		out.write("file0.txt content".getBytes()); //$NON-NLS-1$
		out.close();
		IFileStore folder1 = root.getChild("folder1"); //$NON-NLS-1$
		folder1.mkdir(EFS.NONE, null);
		IFileStore file1a = folder1.getChild("file1a.txt"); //$NON-NLS-1$
		out = file1a.openOutputStream(EFS.NONE, null);
		out.write("folder1/file1a.txt content".getBytes()); //$NON-NLS-1$
		out.close();
		IFileStore file1b = folder1.getChild("file1b.txt"); //$NON-NLS-1$
		out = file1b.openOutputStream(EFS.NONE, null);
		out.write("folder1/file1b.txt content".getBytes()); //$NON-NLS-1$
		out.close();
		IFileStore folder2 = root.getChild("folder2"); //$NON-NLS-1$
		folder2.mkdir(EFS.NONE, null);
		IFileStore file2 = folder2.getChild("file2.txt"); //$NON-NLS-1$
		out = file2.openOutputStream(EFS.NONE, null);
		out.write("folder2/file2.txt content".getBytes()); //$NON-NLS-1$
		out.close();
		IFileStore subfolder1 = folder1.getChild("subfolder1"); //$NON-NLS-1$
		subfolder1.mkdir(EFS.NONE, null);
		IFileStore file3 = subfolder1.getChild("file3.txt"); //$NON-NLS-1$
		out = file3.openOutputStream(EFS.NONE, null);
		out.write("folder1/subfolder1/file3.txt content".getBytes()); //$NON-NLS-1$
		out.close();
	}

	abstract protected void cleanUp() throws CoreException;

	@Test
	public void commonOperations() throws CoreException, IOException {
		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this
				.getClass().getName() + '#' + getMethodName() + "()"); //$NON-NLS-1$
		try {
			for (int i = 0; i < 100; i++) {
				init();
				perfMeter.start();

				// clone (for gitfs)
				root.mkdir(EFS.NONE, null);

				// list children
				root.childStores(EFS.NONE, null);

				// create a new folder
				IFileStore folder2 = root.getChild("folder2"); //$NON-NLS-1$
				IFileStore subfolder2 = folder2.getChild("subfolder2"); //$NON-NLS-1$
				subfolder2.mkdir(EFS.NONE, null);

				// create a new file
				IFileStore file4 = subfolder2.getChild("file4.txt"); //$NON-NLS-1$
				OutputStream out = file4.openOutputStream(EFS.NONE, null);
				out.write("subfolder1/file3.txt content".getBytes()); //$NON-NLS-1$
				out.close();

				// read file content
				IFileStore file2 = root.getChild("folder2/file2.txt"); //$NON-NLS-1$
				InputStream in = file2.openInputStream(EFS.NONE, null);
				byte[] actual = readBytes(in);
				in.close();
				assertEquals("folder2/file2.txt content", new String(actual)); //$NON-NLS-1$

				// modify a file
				out = file2.openOutputStream(EFS.APPEND, null);
				out.write("append me".getBytes()); //$NON-NLS-1$
				out.close();

				// remove a file
				IFileStore file1a = root.getChild("folder1/file1a.txt"); //$NON-NLS-1$
				file1a.delete(EFS.NONE, null);

				// remove a folder
				IFileStore subfolder1 = root.getChild("folder1/subfolder1"); //$NON-NLS-1$
				subfolder1.delete(EFS.SHALLOW, null);

				perfMeter.stop();
				cleanUp();
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
