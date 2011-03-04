/*******************************************************************************
 * Copyright (c)  2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Runs all automated server tests for git support.
 */
@RunWith(Suite.class)
@SuiteClasses({GitUriTest.class, GitDiffTest.class, GitStatusTest.class, GitIndexTest.class, GitCloneTest.class, GitAddTest.class, GitResetTest.class, GitCommitTest.class})
public class AllGitTests {
	//goofy junit4, no class body needed
}
