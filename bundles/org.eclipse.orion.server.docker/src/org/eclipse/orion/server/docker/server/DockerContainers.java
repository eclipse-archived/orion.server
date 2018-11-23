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
package org.eclipse.orion.server.docker.server;

import java.util.ArrayList;
import java.util.List;

/**
 * The response for container list request using the Docker Remote API.
 *  
 * @author Anthony Hunter
 */
public class DockerContainers extends DockerResponse {

	public static final String CONTAINERS = "Containers";

	public static final String CONTAINERS_PATH = "containers";

	private List<DockerContainer> containers;

	public DockerContainers() {
		super();
		this.containers = new ArrayList<DockerContainer>();
	}

	public List<DockerContainer> getContainers() {
		return containers;
	}

	public void addContainer(DockerContainer Container) {
		this.containers.add(Container);
	}

}
