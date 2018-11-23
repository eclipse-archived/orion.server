/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.server.git.objects.Index;
import org.eclipse.orion.server.git.servlets.GitServlet;

public abstract class BaseToIndexConverter {

	public static final BaseToIndexConverter CLONE = new BaseToIndexConverter() {
		@Override
		public IPath getFilePath(URI base) throws URISyntaxException {
			return new Path(base.getPath()).removeFirstSegments(2).makeRelative();
		};
	};

	public static URI getIndexLocation(URI base, BaseToIndexConverter converter) throws URISyntaxException {
		IPath clonePath = converter.getFilePath(base);
		if (clonePath == null)
			return null;
		IPath p = new Path(GitServlet.GIT_URI).append(Index.RESOURCE).append(clonePath).addTrailingSeparator(); 
		return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), base.getQuery(), base.getFragment());
	}

	public abstract IPath getFilePath(URI base) throws URISyntaxException;

}