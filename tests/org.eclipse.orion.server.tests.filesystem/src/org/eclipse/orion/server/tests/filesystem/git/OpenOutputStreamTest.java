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
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;

public class OpenOutputStreamTest extends
		org.eclipse.core.tests.filesystem.OpenOutputStreamTest {

	private IPath repositoryPath;

	protected void doFSSetUp() throws Exception {
		repositoryPath = getRandomLocation();
		URI uri = URIUtil.toURI(repositoryPath); //encoded
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append(uri.toString());
		sb.append("?/");
		baseStore = EFS.getStore(URI.create(sb.toString()));
		baseStore.mkdir(EFS.NONE, null);
	}

	protected void doFSTearDown() throws IOException {
		// delete <temp>/<repo>
		FileSystemHelper.clear(repositoryPath.toFile());
	}
}
