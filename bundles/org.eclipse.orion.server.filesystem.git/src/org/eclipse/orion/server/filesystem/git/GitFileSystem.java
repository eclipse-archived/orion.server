/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.filesystem.git;

import org.eclipse.orion.internal.server.filesystem.git.LogHelper;

import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.filesystem.provider.FileSystem;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.transport.URIish;

public class GitFileSystem extends FileSystem {

	/**
	 * Scheme constant (value "gitfs") indicating the git file system scheme.
	 * <p>
	 * It's "gitfs" instead of "git", so it easier to distinguish between the
	 * file system scheme and the "git" URL protocol.
	 * <p>
	 * An example of a git URL with git transport protocol in the git file
	 * system:
	 * <code>gitfs:/git://host.xz[:port]/path/to/repo.git?/&lt;project&gt;/path/in/workspace</code>.
	 */
	public static final String SCHEME_GIT = "gitfs"; //$NON-NLS-1$
	private static IFileSystem instance;

	public GitFileSystem() {
		super();
		instance = this;
	}

	public static IFileSystem getInstance() {
		return instance;
	}

	@Override
	public IFileStore getStore(URI uri) {
		// gitfs:/...
		if (SCHEME_GIT.equalsIgnoreCase(uri.getScheme())) {
			StringBuffer sb = new StringBuffer();
			sb.append(uri.getPath());
			// /..., strip off leading '/'
			sb.deleteCharAt(0);
			try {
				URIish u = new URIish(sb.toString());
				IPath p = null;
				sb.setLength(0);
				String query = uri.getQuery();
				if (query != null && !query.equals("")) {
					sb.append(query);
					p = new Path(sb.toString());
				}
				// TODO: fragment?
				return new GitFileStore(u, p, uri.getAuthority());
			} catch (URISyntaxException e) {
				LogHelper.log(e);
			}
		}
		return EFS.getNullFileSystem().getStore(uri);
	}

	@Override
	public boolean canWrite() {
		return true;
	}

	@Override
	public boolean canDelete() {
		return canWrite();
	}

}
