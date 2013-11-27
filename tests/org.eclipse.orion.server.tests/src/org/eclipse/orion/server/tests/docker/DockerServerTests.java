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
package org.eclipse.orion.server.tests.docker;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.orion.internal.server.servlets.docker.DockerContainer;
import org.eclipse.orion.internal.server.servlets.docker.DockerContainers;
import org.eclipse.orion.internal.server.servlets.docker.DockerImage;
import org.eclipse.orion.internal.server.servlets.docker.DockerImages;
import org.eclipse.orion.internal.server.servlets.docker.DockerResponse;
import org.eclipse.orion.internal.server.servlets.docker.DockerServer;
import org.eclipse.orion.internal.server.servlets.docker.DockerVersion;
import org.junit.Test;

/**
 * Tests for the docker server.
 *
 * @author Anthony Hunter
 */
public class DockerServerTests {
	private final static String dockerLocation = "http://localhost:4243";

	/**
	 * Test create docker container.
	 * @throws URISyntaxException
	 */
	@Test
	public void testCreateDockerContainer() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);

		String containerName = "testCreateDockerContainer";

		// create the container
		DockerContainer dockerContainer = dockerServer.createDockerContainer("ubuntu", containerName, null);
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.CREATED, dockerContainer.getStatusCode());
		//System.out.println("Created Docker Container: Container Id " + dockerContainer.getId() + " Name " + dockerContainer.getName());

		// delete the container
		DockerResponse dockerResponse = dockerServer.deleteDockerContainer(containerName);
		assertEquals(dockerResponse.getStatusMessage(), DockerResponse.StatusCode.DELETED, dockerResponse.getStatusCode());
		//System.out.println("Deleted Docker Container: Container Id " + dockerContainer.getId());
	}

	/**
	 * Test docker container life cycle.
	 * @throws URISyntaxException 
	 */
	@Test
	public void testDockerContainerLifeCycle() throws URISyntaxException {
		// the name of our docker container
		String containerName = "lifecycle";

		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);

		// make sure docker is running
		DockerVersion dockerVersion = dockerServer.getDockerVersion();
		assertEquals(dockerVersion.getStatusMessage(), DockerResponse.StatusCode.OK, dockerVersion.getStatusCode());
		System.out.println("Docker Server " + dockerLocation + " is running version " + dockerVersion.getVersion());

		// make sure the container does not exist
		DockerContainer dockerContainer = dockerServer.getDockerContainer(containerName);
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.NO_SUCH_CONTAINER, dockerContainer.getStatusCode());
		System.out.println("Docker Container " + containerName + " does not exist");

		// create the container
		dockerContainer = dockerServer.createDockerContainer("ubuntu", containerName, null);
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.CREATED, dockerContainer.getStatusCode());
		System.out.println("Docker Container " + containerName + " status is " + dockerContainer.getStatus());

		// start the container
		dockerContainer = dockerServer.startDockerContainer(dockerContainer.getId(), null);
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.STARTED, dockerContainer.getStatusCode());
		System.out.println("Docker Container " + containerName + " status is " + dockerContainer.getStatus());

		// attach to the container and run some commands
		String command = "cat /etc/lsb-release\n";
		DockerResponse dockerResponse = dockerServer.attachDockerContainer(dockerContainer.getId(), command);
		assertEquals(dockerVersion.getStatusMessage(), DockerResponse.StatusCode.OK, dockerVersion.getStatusCode());
		System.out.println(dockerResponse.getStatusMessage());

		command = "ls\n";
		dockerResponse = dockerServer.attachDockerContainer(dockerContainer.getId(), command);
		assertEquals(dockerVersion.getStatusMessage(), DockerResponse.StatusCode.OK, dockerVersion.getStatusCode());
		System.out.println(dockerResponse.getStatusMessage());

		command = "ls OrionContent\n";
		dockerResponse = dockerServer.attachDockerContainer(dockerContainer.getId(), command);
		assertEquals(dockerVersion.getStatusMessage(), DockerResponse.StatusCode.OK, dockerVersion.getStatusCode());
		System.out.println(dockerResponse.getStatusMessage());

		// stop the container
		dockerContainer = dockerServer.stopDockerContainer(dockerContainer.getId());
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.STOPPED, dockerContainer.getStatusCode());
		System.out.println("Docker Container " + containerName + " status is " + dockerContainer.getStatus());

		// delete the container
		dockerResponse = dockerServer.deleteDockerContainer(containerName);
		assertEquals(dockerResponse.getStatusMessage(), DockerResponse.StatusCode.DELETED, dockerResponse.getStatusCode());
		System.out.println("Docker Container " + containerName + " status is deleted");

	}

	/**
	 * Test get docker container.
	 * @throws URISyntaxException
	 */
	public void testGetDockerContainer() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerContainer dockerContainer = dockerServer.getDockerContainer("admin");
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.OK, dockerContainer.getStatusCode());

		dockerContainer = dockerServer.getDockerContainer("doesnotexist");
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.NO_SUCH_CONTAINER, dockerContainer.getStatusCode());
	}

	/**
	 * Test get docker containers.
	 * @throws URISyntaxException
	 */
	public void testGetDockerContainers() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerContainers dockerContainers = dockerServer.getDockerContainers();
		assertEquals(dockerContainers.getStatusMessage(), DockerResponse.StatusCode.OK, dockerContainers.getStatusCode());
		//System.out.println("Docker Containers: ");
		//for (DockerContainer dockerContainer : dockerContainers.getContainers()) {
		//	System.out.println("Container Id " + dockerContainer.getId() + " Image " + dockerContainer.getImage() + " Name " + dockerContainer.getName());
		//}
	}

	/**
	 * Test get docker image.
	 * @throws URISyntaxException
	 */
	public void testGetDockerImage() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerImage dockerImage = dockerServer.getDockerImage("ubuntu");
		assertEquals(dockerImage.getStatusMessage(), DockerResponse.StatusCode.OK, dockerImage.getStatusCode());

		dockerImage = dockerServer.getDockerImage("doesnotexist");
		assertEquals(dockerImage.getStatusMessage(), DockerResponse.StatusCode.NO_SUCH_IMAGE, dockerImage.getStatusCode());
	}

	/**
	 * Test get docker images.
	 * @throws URISyntaxException
	 */
	public void testGetDockerImages() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerImages dockerImages = dockerServer.getDockerImages();
		assertEquals(dockerImages.getStatusMessage(), DockerResponse.StatusCode.OK, dockerImages.getStatusCode());
		//System.out.println("Docker Images: ");
		//for (DockerImage dockerImage : dockerImages.getImages()) {
		//	System.out.print("Image Id " + dockerImage.getId() + " Repository " + dockerImage.getRepository() + " Tags { ");
		//	for (String tag : dockerImage.getTags()) {
		//		System.out.print(tag + " ");
		//	}
		//	System.out.println("}");
		//}
	}

	/**
	 * Test get docker version, if this test fails docker is likely not running.
	 * @throws URISyntaxException 
	 */
	public void testGetDockerVersion() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerVersion dockerVersion = dockerServer.getDockerVersion();
		assertEquals(dockerVersion.getStatusMessage(), DockerResponse.StatusCode.OK, dockerVersion.getStatusCode());
		assertEquals("unknown docker version", "0.6.7", dockerVersion.getVersion());
	}

}
