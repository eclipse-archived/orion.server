/*******************************************************************************
 * Copyright (c)  2011, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Runs all automated server tests for git support.
 */
@RunWith(Suite.class)
@SuiteClasses({GitUriTest.class, //
		GitCloneTest.class, //
		GitInitTest.class, //
		GitDiffTest.class, //
		GitStatusTest.class, //
		GitIndexTest.class, //
		GitAddTest.class, //
		GitResetTest.class, //
		GitCommitTest.class, //
		GitConfigTest.class, //
		GitRemoteTest.class, //
		GitFetchTest.class, //
		GitMergeTest.class, //
		GitMergeSquashTest.class, //
		GitRebaseTest.class, //
		GitPushTest.class, //
		GitLogTest.class, //
		GitTagTest.class, //
		GitUtilsTest.class, //
		GitCheckoutTest.class, //
		GitBranchTest.class, //
		GitCherryPickTest.class, //
		GitPullTest.class, //
		GitApplyPatchTest.class})
public class AllGitTests {
	public static IPath getRandomLocation() {
		return FileSystemHelper.getRandomLocation(FileSystemHelper.getTempDir());
	}
}
