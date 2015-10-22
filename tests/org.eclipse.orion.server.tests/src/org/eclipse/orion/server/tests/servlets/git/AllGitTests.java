/*******************************************************************************
 * Copyright (c)  2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import org.eclipse.orion.server.tests.core.EndingAwareLineReaderTest;
import org.eclipse.orion.server.tests.core.IOUtilitiesTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Runs all automated server tests for git support.
 */
@RunWith(Suite.class)
@SuiteClasses({GitUriTest.class, //
		GitBlameTest.class, //
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
		GitRevertTest.class, //
		GitPullTest.class, //
		GitApplyPatchTest.class, //
		GitStashTest.class, //
		GitSubmoduleTest.class, //
		IOUtilitiesTest.class, //
		EndingAwareLineReaderTest.class})
public class AllGitTests {
	//goofy junit4, no class body needed
}
