/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.junit.BeforeClass;
import org.junit.Test;

public class GitLogPerformanceTest {
	private final static int LOOPS = 10;

	private final List<Repository> toClose = new ArrayList<Repository>();

	private final OutputStream os = NullOutputStream.INSTANCE/*System.out*/;

	private final PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os)));

	char[] outbuffer = new char[Constants.OBJECT_ID_LENGTH * 2];

	private final static File repo = new File("D:\\workspace\\eclipse\\egit\\egit\\.git");

	private static String cmd;

	@BeforeClass
	public static void init() {
		assertTrue(repo.exists());
		if ("win32".equals(System.getProperty("osgi.os"))) {
			cmd = "d:/apps/cygwin/bin/";
		}
	}

	@Test
	public void logCommand() throws Exception {
		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this.getClass().getName() + '#' + getMethodName() + "()"); //$NON-NLS-1$

		Git git = new Git(new FileRepository(repo));
		try {
			for (int i = 0; i < LOOPS; i++) {
				perfMeter.start();
				Iterable<RevCommit> commits = git.log().call();
				for (RevCommit obj : commits) {
					// see org.eclipse.jgit.pgm.RevList.show(ObjectWalk, RevObject)
					obj.getId().copyTo(outbuffer, out);
					out.print(' ');
					out.println(obj.getFullMessage());
				}
				perfMeter.stop();

				toClose.add(git.getRepository());
				assertNotNull(git);
			}
			perfMeter.commit();
			perf.assertPerformance(perfMeter);
		} finally {
			perfMeter.dispose();
		}
	}

	@Test
	public void cgit() throws IOException, InterruptedException {
		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this.getClass().getName() + '#' + getMethodName() + "()"); //$NON-NLS-1$
		try {
			for (int i = 0; i < LOOPS; i++) {

				perfMeter.start();
				Process proc = Runtime.getRuntime().exec(cmd + "git log", null, repo.getParentFile());
				InputStream is = proc.getInputStream();
				int b;
				while ((b = is.read()) != -1)
					os.write(b);
				int exitVal = proc.waitFor();
				perfMeter.stop();
				assertEquals(0, exitVal);
			}
			perfMeter.commit();
			perf.assertPerformance(perfMeter);
		} finally {
			perfMeter.dispose();
		}
	}

	private static String getMethodName() {
		final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		return ste[3].getMethodName();
	}
}
