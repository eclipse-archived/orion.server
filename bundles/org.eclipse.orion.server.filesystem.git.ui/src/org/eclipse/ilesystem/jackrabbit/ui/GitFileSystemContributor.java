/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ilesystem.jackrabbit.ui;

import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ide.fileSystem.FileSystemContributor;

public class GitFileSystemContributor extends FileSystemContributor {

	public GitFileSystemContributor() {
		super();
	}

	@Override
	public URI browseFileSystem(String initialPath, Shell shell) {
		try {
			// TODO: open JGit's repo view
			/*
			 * TODO:
			 * Git natively supports ssh, git, http, https, ftp, ftps, and rsync
			 * protocols. The following syntaxes may be used with them:
			 * 
			 * ssh://[user@]host.xz[:port]/path/to/repo.git/
			 * git://host.xz[:port]/path/to/repo.git/
			 * http[s]://host.xz[:port]/path/to/repo.git/
			 * ftp[s]://host.xz[:port]/path/to/repo.git/
			 * rsync://host.xz/path/to/repo.git/
			 * 
			 * An alternative scp-like syntax may also be used with the ssh
			 * protocol:
			 * 
			 * [user@]host.xz:path/to/repo.git/
			 * 
			 * The ssh and git protocols additionally support ~username
			 * expansion:
			 * 
			 * ssh://[user@]host.xz[:port]/~[user]/path/to/repo.git/
			 * git://host.xz[:port]/~[user]/path/to/repo.git/
			 * 
			 * [user@]host.xz:/~[user]/path/to/repo.git/
			 * 
			 * For local repositories, also supported by git natively, the
			 * following syntaxes may be used:
			 * 
			 * /path/to/repo.git/
			 * file:///path/to/repo.git/
			 */
			
			return new URI(GitFileSystem.SCHEME_GIT + ":/" + "git://localhost/test.git?/test");
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public URI getURI(String string) {
		try {
			if (string.startsWith(GitFileSystem.SCHEME_GIT))
				return new URI(string);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
