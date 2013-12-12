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
package org.eclipse.orion.server.docker;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.orion.server.docker.servlets.DockerResponse;

/**
 * The response for image list request using the Docker Remote API.
 *  
 * @author Anthony Hunter
 */
public class DockerImages extends DockerResponse {
	private List<DockerImage> images;

	public DockerImages() {
		super();
		this.images = new ArrayList<DockerImage>();
	}

	public List<DockerImage> getImages() {
		return images;
	}

	public void addImage(DockerImage image) {
		this.images.add(image);
	}

}
