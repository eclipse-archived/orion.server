/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
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

public abstract class BaseToConfigEntryConverter {

	public static final BaseToConfigEntryConverter REMOVE_FIRST_2 = new BaseToConfigEntryConverter() {
		@Override
		public URI baseToConfigEntryLocation(URI base, String entryKey) throws URISyntaxException {
			IPath p = new Path(base.getPath());
			p = p.uptoSegment(1).append(GitConstants.CONFIG_RESOURCE).append(entryKey).addTrailingSeparator().append(p.removeFirstSegments(2));
			return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), p.toString(), base.getQuery(), base.getFragment());
		};
	};

	public abstract URI baseToConfigEntryLocation(URI base, String entryKey) throws URISyntaxException;
}
