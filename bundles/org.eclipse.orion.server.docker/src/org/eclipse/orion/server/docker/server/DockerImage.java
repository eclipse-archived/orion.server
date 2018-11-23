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
package org.eclipse.orion.server.docker.server;

import java.util.ArrayList;
import java.util.List;


/**
 * The response for image requests using the Docker Remote API.
 *  
 * @author Anthony Hunter
 */
public class DockerImage extends DockerResponse {
	
	public static final String CONFIG = "config";

	public static final String CREATED = "Created";

	public static final String EXPOSED_PORTS = "ExposedPorts";
	
	public static final String ID = "Id";

	public static final String IMAGE = "Image";

	public static final String IMAGE_PATH = "image";

	public static final String REPOSITORY = "Repository";

	public static final String REPOTAGS = "RepoTags";

	public static final String SIZE = "Size";
	
	public static final String TAG = "Tag";

	public static final String VIRTUAL_SIZE = "VirtualSize";

	private String created;

	private String id;
	
	private List<String> ports;

	private String repository;

	private long size;

	private String tag;

	private long virtualSize;

	public DockerImage() {
		super();
		ports = new ArrayList<String>();
	}

	public void addPort(String port) {
		ports.add(port);
	}

	public String getCreated() {
		return created;
	}

	public String getId() {
		return id;
	}
	
	public List<String> getPorts() {
		return ports;
	}

	public String getRepository() {
		return repository;
	}

	public long getSize() {
		return size;
	}

	public String getTag() {
		return tag;
	}

	public long getVirtualSize() {
		return virtualSize;
	}

	public void setCreated(String created) {
		this.created = created;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setRepository(String repository) {
		this.repository = repository;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public void setVirtualSize(long virtualSize) {
		this.virtualSize = virtualSize;
	}
}
