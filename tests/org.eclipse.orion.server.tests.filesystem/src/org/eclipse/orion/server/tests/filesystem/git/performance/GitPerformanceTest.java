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
package org.eclipse.orion.server.tests.filesystem.git.performance;

import java.io.IOException;
import java.net.URI;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.orion.internal.server.filesystem.git.Utils;
import org.eclipse.orion.server.filesystem.git.GitFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;
import org.eclipse.orion.server.tests.filesystem.performance.PerformanceTest;

public class GitPerformanceTest extends PerformanceTest {
	private GitFileSystem fs = new GitFileSystem();
	private IPath repositoryPath;

	protected void initRoot() {
		repositoryPath = getRandomLocation();
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s + "?/");
		root = (GitFileStore) fs.getStore(uri);
	}
	
	protected void init() throws CoreException, IOException {
		super.init();
		// remove local clone
		FileSystemHelper.clear(((GitFileStore)root).getLocalFile());
	}

	public void cleanUp() {
		FileSystemHelper.clear(((GitFileStore)root).getLocalFile());
		FileSystemHelper.clear(repositoryPath.toFile());
	}

}
