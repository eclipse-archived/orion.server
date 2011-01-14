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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.orion.internal.server.filesystem.git.Utils;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;

public class PutInfoTest extends org.eclipse.core.tests.filesystem.PutInfoTest {

	private IPath repositoryPath;

	protected URI getFileStoreUri() throws UnsupportedEncodingException {
		repositoryPath = getRandomLocation();
		String s = Utils.encodeLocalPath(repositoryPath.toString());
		return URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s + "?/");
	}

	protected void fileSystemSetUp() throws IOException {
	}

	protected void fileSystemTearDown() throws IOException {
		// delete <temp>/<repo>
		FileSystemHelper.clear(repositoryPath.toFile());
	}
}
