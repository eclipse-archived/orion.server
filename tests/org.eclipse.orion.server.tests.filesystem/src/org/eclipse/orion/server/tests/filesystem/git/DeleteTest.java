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

import java.net.URI;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.orion.server.filesystem.git.GitFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;

public class DeleteTest extends org.eclipse.core.tests.filesystem.DeleteTest {

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

	@Override
	protected void doFSTearDown() throws Exception {
		// nothing to do
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		// remove the repository
		FileSystemHelper.clear(repositoryPath.toFile());
		// remove the clone
		FileSystemHelper.clear(((GitFileStore)baseStore).getLocalFile());
	}
}
