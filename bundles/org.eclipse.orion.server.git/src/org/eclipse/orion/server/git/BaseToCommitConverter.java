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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.server.git.objects.Commit;

public abstract class BaseToCommitConverter {

	public static final BaseToCommitConverter REMOVE_FIRST_2 = new BaseToCommitConverter() {
		@Override
		public URI baseToCommitLocation(URI base, String commit, String path) throws URISyntaxException {
			IPath p = new Path(base.getPath());
			p = p.uptoSegment(1).append(Commit.RESOURCE).append(commit).addTrailingSeparator().append(p.removeFirstSegments(2));
			if (path != null)
				p = p.append(path);
			return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), getQuery(base.getQuery()), base.getFragment());
		};
	};

	public static final BaseToCommitConverter REMOVE_FIRST_3 = new BaseToCommitConverter() {
		@Override
		public URI baseToCommitLocation(URI base, String commit, String path) throws URISyntaxException {
			IPath p = new Path(base.getPath());
			p = p.uptoSegment(1).append(Commit.RESOURCE).append(commit).addTrailingSeparator().append(p.removeFirstSegments(3));
			if (path != null)
				p = p.append(path);
			return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), getQuery(base.getQuery()), base.getFragment());
		};
	};

	public static URI getCommitLocation(URI base, String commit, BaseToCommitConverter converter) throws IOException, URISyntaxException {
		return getCommitLocation(base, commit, null, converter);
	}

	public static URI getCommitLocation(URI base, String commit, String path, BaseToCommitConverter converter) throws URISyntaxException {
		return converter.baseToCommitLocation(base, commit, path);
	}

	protected String query;

	protected abstract URI baseToCommitLocation(URI base, String commit, String path) throws URISyntaxException;

	public BaseToCommitConverter setQuery(String query) {
		this.query = query;
		return this;
	}

	public String getQuery(String baseQuery) {
		String result = query != null ? query : baseQuery;
		// TODO: still not thread-safe, replace with a factory
		this.query = null;
		return result;
	}
}
