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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.*;
import org.eclipse.orion.internal.server.filesystem.git.Utils;
import org.eclipse.orion.server.filesystem.git.GitFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;
import org.junit.Ignore;
import org.junit.Test;

public class UtilsTest {

	GitFileSystem fs = new GitFileSystem();
	
	// org.eclipse.internal.filesystem.git.Utils.isValidRepository(URL)

	@Test
	public void validRepository() throws MalformedURLException,
			URISyntaxException {
		// TODO: need to set up and run local git repo before running the test
		URI uri = new URI(GitFileSystem.SCHEME_GIT + "://test/"
				+ "git://localhost/repo.git?/anything");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		assertTrue(Utils.isValidRemote(gfs));
	}

	@Ignore
	public void offlineValidRepository() throws MalformedURLException,
			URISyntaxException {
		// TODO: test valid url but when the repo is down
		URI uri = new URI(GitFileSystem.SCHEME_GIT + "://test/"
				+ "git://localhost/repo.git?/anything");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		assertFalse(Utils.isValidRemote(gfs));
	}

	@Test
	public void invalidRepository() throws MalformedURLException,
			URISyntaxException {
		URI uri = new URI(GitFileSystem.SCHEME_GIT + "://test/"
				+ "git://localhost/not-there.git");
		GitFileStore gfs = (GitFileStore) fs.getStore(uri);
		assertFalse(Utils.isValidRemote(gfs));
	}
}
