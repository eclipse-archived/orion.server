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
package org.eclipse.orion.internal.server.filesystem.git;

import java.net.URISyntaxException;
import java.net.URL;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.jgit.transport.URIish;

public class Utils {

	public static URIish toURIish(final URL u) throws URISyntaxException {
		String s = u.toString();
		// ignore query
		s = s.replace("?" + u.getQuery(), ""); //$NON-NLS-1$ //$NON-NLS-2$
		return new URIish(s);
	}

	public static IFileStore getRoot(IFileStore gfs) {
		IFileStore parent = null;
		while ((parent = gfs.getParent()) != null) {
			gfs = parent;
		}
		return gfs;
	}
}
