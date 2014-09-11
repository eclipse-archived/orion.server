/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core.metastore;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;

/**
 * A snapshot of information about a single project.
 */
public class ProjectInfo extends MetadataInfo {
	private URI contentLocation;
	private String workspaceId;

	/**
	 * Returns the absolute location of the contents of this project.
	 * <p>
	 * This method never returns null.
	 * </p>
	 * @return The location of the contents of this project
	 */
	public URI getContentLocation() {
		return contentLocation;
	}

	/**
	 * @throws CoreException 
	 */
	public IFileStore getProjectStore() throws CoreException {
		if (contentLocation == null) {
			return null;
		}
		return EFS.getStore(getContentLocation());
	}

	/**
	 * Returns the workspace id of the workspace that owns this project.
	 * @return the workspace id.
	 */
	public String getWorkspaceId() {
		return workspaceId;
	}

	/**
	 * Sets the absolute location of the contents of this project.
	 */
	public void setContentLocation(URI contentURI) {
		if (contentURI.getUserInfo() == null) {
			contentLocation = contentURI;
		} else {
			try {
				//strip out credential information
				contentLocation = new URI(contentURI.getScheme(), null, contentURI.getHost(), contentURI.getPort(), contentURI.getPath(), contentURI.getQuery(), contentURI.getFragment());
			} catch (URISyntaxException e) {
				//should never happen because we are stripping info from a valid URI
				throw new RuntimeException(e);
			}
		}
	}
	
	/**
	 * Sets the workspace id of the workspace that owns this project.
	 * @param userId the workspace id.
	 */
	public void setWorkspaceId(String workspaceId) {
		this.workspaceId = workspaceId;
	}
}
