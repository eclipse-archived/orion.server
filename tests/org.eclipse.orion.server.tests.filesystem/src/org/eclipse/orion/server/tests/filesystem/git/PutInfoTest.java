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
package org.eclipse.orion.server.tests.filesystem.git;

import java.io.IOException;
import java.net.URI;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.orion.internal.server.filesystem.git.Utils;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;

public class PutInfoTest extends org.eclipse.core.tests.filesystem.PutInfoTest {

	private IPath repositoryPath;

	protected void doFSSetUp() throws Exception {
		repositoryPath = getRandomLocation();
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		baseStore = EFS.getStore(URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s + "?/"));
		baseStore.mkdir(EFS.NONE, null);
	}

	protected void doFSTearDown() throws IOException {
		// delete <temp>/<repo>
		FileSystemHelper.clear(repositoryPath.toFile());
	}
}
