/*******************************************************************************
 *  Copyright (c) 2011 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.filesystem.performance;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.provider.FileSystem;
import org.eclipse.core.internal.filesystem.local.LocalFileSystem;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.tests.harness.FileSystemHelper;

public class LocalPerformanceTest extends PerformanceTest {
	private FileSystem fs = new LocalFileSystem();

	@Override
	protected void initRoot() throws CoreException {
		try {
			URI uri = new URI("file", getRandomLocation().toString() + "/local/",
					null);
			root = fs.getStore(uri);
		} catch (URISyntaxException e) {
			throw new CoreException(new Status(IStatus.ERROR,
					"tests.filesystem", e.getMessage(), e));
		}
	}

	public void cleanUp() throws CoreException {
		FileSystemHelper.clear(root.toLocalFile(EFS.NONE, null));
	}

}
