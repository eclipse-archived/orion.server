/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.server.logs.objects;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.logs.LogConstants;
import org.eclipse.orion.server.logs.servlets.LogServlet;

@ResourceDescription(type = RollingFileAppenderResource.TYPE)
public class RollingFileAppenderResource extends FileAppenderResource {
	public static final String RESOURCE = "rollingFileAppender"; //$NON-NLS-1$
	public static final String TYPE = "RollingFileAppender"; //$NON-NLS-1$

	@PropertyDescription(name = ProtocolConstants.KEY_LOCATION)
	public URI getLocation() throws URISyntaxException {
		IPath path = new Path(LogServlet.LOG_URI).append(RollingFileAppenderResource.RESOURCE)
				.append(getName());
		return createUriWithPath(path);
	}

	@PropertyDescription(name = LogConstants.KEY_APPENDER_DOWNLOAD_LOCATION)
	public URI getDownloadLocation() throws URISyntaxException {
		IPath path = new Path(LogServlet.LOG_URI).append(RollingFileAppenderResource.RESOURCE)
				.append(getName()).append(LogConstants.KEY_APPENDER_DOWNLOAD);
		return createUriWithPath(path);
	}
}
