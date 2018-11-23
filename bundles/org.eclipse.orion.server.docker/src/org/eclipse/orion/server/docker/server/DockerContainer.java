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

/**
 * The response for container requests using the Docker Remote API.
 *  
 * @author Anthony Hunter
 */
public class DockerContainer extends DockerResponse {

	public static final String ATTACH_WS = "attachWsURI";
	
	public static final String COMMAND = "Command";

	public static final String CONTAINER = "Container";

	public static final String CONTAINER_PATH = "container";

	public static final String CONTAINER_CONNECT_PATH = "connect";

	public static final String CONTAINER_DISCONNECT_PATH = "disconnect";

	public static final String CREATED = "Created";

	public static final String ID = "Id";

	public static final String IMAGE = "Image";

	public static final String NAME = "Name";

	public static final String NAMES = "Names";

	public static final String PORTS = "Ports";

	public static final String SIZE_ROOT_FS = "SizeRootFs";

	public static final String SIZE_RW = "SizeRw";

	public static final String STATUS = "Status";

	private String command;

	private String created;

	private String id;

	private String image;

	private String name;

	private String ports;

	private int size;

	private int sizeRootFs;

	private String status;

	public String getCommand() {
		return command;
	}

	public String getCreated() {
		return created;
	}

	public String getId() {
		return id;
	}

	/**
	 * Get the short version of the container id, the first 12 characters.
	 */
	public String getIdShort() {
		return id.substring(0, 12);
	}

	public String getImage() {
		return image;
	}

	public String getName() {
		return name;
	}

	public String getPorts() {
		return ports;
	}

	public int getSize() {
		return size;
	}

	public int getSizeRootFs() {
		return sizeRootFs;
	}

	public String getStatus() {
		return status;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public void setCreated(String created) {
		this.created = created;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPorts(String ports) {
		this.ports = ports;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public void setSizeRootFs(int sizeRootFs) {
		this.sizeRootFs = sizeRootFs;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
