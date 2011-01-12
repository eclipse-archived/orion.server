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
package org.eclipse.orion.internal.server.filesystem.git;

import org.eclipse.orion.server.filesystem.git.GitFileStore;

import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;

public class Utils {
	
	public static URIish toURIish(final URL u) throws URISyntaxException {
		String s = u.toString();
		// ignore query
		s = s.replace("?" + u.getQuery(), "");
		s = s.replace('+', ' ');
		return new URIish(s);
	}
	
	public static String encodeLocalPath(final String s) {
		String r = new String(s);
		r = r.replace(' ', '+');
		r = r.replace("/", "%5C"); // slash to backslash then encode
		r = r.replace("\\", "%5C"); // just encode
		// String s = URLEncoder.encode(repositoryPath.toString(), "UTF-8");
		return r;
	}
	
	public static boolean isValidRemote(GitFileStore gfs) {
		Transport transport = null;
		try {
			URIish remote = toURIish(gfs.getUrl());
			Repository local = gfs.getLocalRepo();
			if (!Transport.canHandleProtocol(remote, FS.DETECTED))
				return false;
			transport = Transport.open(local, remote);
			transport.openFetch().close();
			return true;
		} catch (Exception e) {
			// ignore
		} finally {
			if (transport != null)
				transport.close();
		}
		return false;
	}

	public static IFileStore getRoot(IFileStore gfs) {
		IFileStore parent = null;
		while ((parent = gfs.getParent()) != null) {
			gfs = parent;
		}
		return (IFileStore) gfs;
	}
}
