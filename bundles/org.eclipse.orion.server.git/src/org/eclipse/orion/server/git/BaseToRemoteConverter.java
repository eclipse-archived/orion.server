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
import org.eclipse.orion.server.git.objects.Remote;
import org.eclipse.orion.server.git.servlets.GitServlet;

public abstract class BaseToRemoteConverter {

	public static final BaseToRemoteConverter FILE = new BaseToRemoteConverter() {
		@Override
		public URI baseToRemoteLocation(URI base, String remote, String branch) throws URISyntaxException {
			IPath p = new Path(base.getPath());
			p = new Path(GitServlet.GIT_URI).append(Remote.RESOURCE).append(remote).append(branch).addTrailingSeparator().append(p);
			return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), base.getQuery(), base.getFragment());
		};
	};

	public static final BaseToRemoteConverter REMOVE_FIRST_2 = new BaseToRemoteConverter() {
		@Override
		public URI baseToRemoteLocation(URI base, String remote, String branch) throws URISyntaxException {
			IPath p = new Path(base.getPath());
			p = p.uptoSegment(1).append(Remote.RESOURCE).append(remote).append(branch).addTrailingSeparator().append(p.removeFirstSegments(2));
			return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), base.getQuery(), base.getFragment());
		};
	};

	public abstract URI baseToRemoteLocation(URI base, String remote, String branch) throws URISyntaxException;
}
