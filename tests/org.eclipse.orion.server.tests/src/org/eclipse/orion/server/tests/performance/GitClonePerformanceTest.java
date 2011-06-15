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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class GitClonePerformanceTest {

	private final static int LOOPS = 10;

	private static URIish orionClientHttp;
	private static URIish orionClientGit;
	private static String cmd = "";
	private final List<Repository> toClose = new ArrayList<Repository>();

	@BeforeClass
	public static void init() throws URISyntaxException {
		orionClientHttp = new URIish("http://git.eclipse.org/gitroot/e4/org.eclipse.orion.client.git");
		orionClientGit = new URIish("git://git.eclipse.org/gitroot/e4/org.eclipse.orion.client.git");
		if ("win32".equals(System.getProperty("osgi.os"))) {
			cmd = "d:/apps/cygwin/bin/";
		}
	}

	@After
	public void closeRepository() throws IOException {
		for (Repository r : toClose) {
			r.close();
			FileUtils.delete(r.getDirectory().getParentFile(), FileUtils.RECURSIVE);
		}
		toClose.clear();
	}

	@Test
	public void cloneWithJGitOverHttp() {
		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this.getClass().getName() + '#' + getMethodName() + "()"); //$NON-NLS-1$
		try {
			for (int i = 0; i < LOOPS; i++) {
				perfMeter.start();
				Git git = Git.cloneRepository().setURI(orionClientHttp.toString()).setDirectory(getRandomLocation().toFile()).call();
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
	public void cloneWithJGitOverGit() {
		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this.getClass().getName() + '#' + getMethodName() + "()"); //$NON-NLS-1$
		try {
			for (int i = 0; i < LOOPS; i++) {
				perfMeter.start();
				Git git = Git.cloneRepository().setURI(orionClientGit.toString()).setDirectory(getRandomLocation().toFile()).call();
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
	public void cloneWithConsoleGitOverHttp() throws IOException, InterruptedException {
		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this.getClass().getName() + '#' + getMethodName() + "()"); //$NON-NLS-1$
		try {
			for (int i = 0; i < LOOPS; i++) {

				perfMeter.start();
				File destination = getRandomLocation().toFile();
				Process proc = Runtime.getRuntime().exec(cmd + "git clone " + orionClientHttp.toString() + " '" + destination + "'");
				int exitVal = proc.waitFor();
				perfMeter.stop();

				assertEquals(0, exitVal);
				FileRepository repository = new FileRepository(new File(destination, Constants.DOT_GIT));
				toClose.add(repository);
				assertEquals(RepositoryState.SAFE, repository.getRepositoryState());

			}
			perfMeter.commit();
			perf.assertPerformance(perfMeter);
		} finally {
			perfMeter.dispose();
		}
	}

	@Test
	public void cloneWithConsoleGitOverGit() throws IOException, InterruptedException {
		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this.getClass().getName() + '#' + getMethodName() + "()"); //$NON-NLS-1$
		try {
			for (int i = 0; i < LOOPS; i++) {

				perfMeter.start();
				File destination = getRandomLocation().toFile();
				Process proc = Runtime.getRuntime().exec(cmd + "git clone " + orionClientGit.toString() + " '" + destination + "'");
				int exitVal = proc.waitFor();
				perfMeter.stop();

				assertEquals(0, exitVal);
				FileRepository repository = new FileRepository(new File(destination, Constants.DOT_GIT));
				toClose.add(repository);
				assertEquals(RepositoryState.SAFE, repository.getRepositoryState());

			}
			perfMeter.commit();
			perf.assertPerformance(perfMeter);
		} finally {
			perfMeter.dispose();
		}
	}

	private IPath getRandomLocation() {
		return FileSystemHelper.getRandomLocation(FileSystemHelper.getTempDir());
	}

	private static String getMethodName() {
		final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		return ste[3].getMethodName();
	}

}
