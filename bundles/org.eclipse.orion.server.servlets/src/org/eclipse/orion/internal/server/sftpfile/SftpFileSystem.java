/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.sftpfile;

import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileSystem;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * A file system implementation that interacts with a remote file system over SFTP.
 */
public class SftpFileSystem extends FileSystem {

	@Override
	public IFileStore getStore(URI uri) {
		IPath path = new Path(uri.getPath());
		URI host;
		try {
			host = new URI(uri.getScheme(), uri.getAuthority(), null, null, null);
		} catch (URISyntaxException e) {
			//invalid URI for this file system
			return EFS.getNullFileSystem().getStore(path);
		}
		return new SftpFileStore(host, path);
	}
}
