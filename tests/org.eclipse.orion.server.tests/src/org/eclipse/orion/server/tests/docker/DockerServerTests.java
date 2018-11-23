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
package org.eclipse.orion.server.tests.docker;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.orion.server.docker.server.DockerContainer;
import org.eclipse.orion.server.docker.server.DockerContainers;
import org.eclipse.orion.server.docker.server.DockerImage;
import org.eclipse.orion.server.docker.server.DockerImages;
import org.eclipse.orion.server.docker.server.DockerResponse;
import org.eclipse.orion.server.docker.server.DockerServer;
import org.eclipse.orion.server.docker.server.DockerVersion;
import org.junit.Test;

/**
 * Tests for the docker server.
 *
 * @author Anthony Hunter
 */
public class DockerServerTests {
	private final static String dockerLocation = "http://localhost:4243";
	private final static String dockerPortStart = "12000";
	private final static String dockerPortEnd = "13000";
	private final static String dockerUserId = "1000";
	private final static String dockerGroupId = "100";

	/**
	 * Test create docker container.
	 * @throws URISyntaxException
	 */
	public void testCreateDockerContainer() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI, dockerLocationURI, dockerPortStart, dockerPortEnd, dockerUserId, dockerGroupId);

		// create the container
		DockerContainer dockerContainer = dockerServer.createDockerContainer("orion-base", "user", null);
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.CREATED, dockerContainer.getStatusCode());
		System.out.println("Created Docker Container: Container Id " + dockerContainer.getId() + " Name " + dockerContainer.getName());

		// delete the container
		//DockerResponse dockerResponse = dockerServer.deleteDockerContainer(containerName);
		//assertEquals(dockerResponse.getStatusMessage(), DockerResponse.StatusCode.DELETED, dockerResponse.getStatusCode());
		//System.out.println("Deleted Docker Container: Container Id " + dockerContainer.getId());
	}

	/**
	 * Test create docker container.
	 * @throws URISyntaxException
	 */
	public void testCreateDockerOrionBaseImage() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI, dockerLocationURI, dockerPortStart, dockerPortEnd, dockerUserId, dockerGroupId);

		// create the image
		DockerImage dockerImage = dockerServer.createDockerOrionBaseImage();
		assertEquals(dockerImage.getStatusMessage(), DockerResponse.StatusCode.CREATED, dockerImage.getStatusCode());
		System.out.println("Created Docker Image: Image Id " + dockerImage.getId() + " Repository " + dockerImage.getRepository());
	}

	/**
	 * Test docker container life cycle.
	 * @throws URISyntaxException 
	 */
	public void testDockerContainerLifeCycle() throws URISyntaxException {
		// the name of our docker container
		String containerName = "lifecycle";

		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI, dockerLocationURI, dockerPortStart, dockerPortEnd, dockerUserId, dockerGroupId);

		// make sure docker is running
		DockerVersion dockerVersion = dockerServer.getDockerVersion();
		assertEquals(dockerVersion.getStatusMessage(), DockerResponse.StatusCode.OK, dockerVersion.getStatusCode());
		System.out.println("Docker Server " + dockerLocation + " is running version " + dockerVersion.getVersion());

		// make sure the container does not exist
		DockerContainer dockerContainer = dockerServer.getDockerContainer(containerName);
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.NO_SUCH_CONTAINER, dockerContainer.getStatusCode());
		System.out.println("Docker Container " + containerName + " does not exist");

		// create the container
		dockerContainer = dockerServer.createDockerContainer("orion-base", containerName, null);
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.CREATED, dockerContainer.getStatusCode());
		System.out.println("Docker Container " + containerName + " status is " + dockerContainer.getStatus());

		// start the container
		dockerContainer = dockerServer.startDockerContainer(dockerContainer.getId(), null, null);
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
	 * Test docker user image life cycle.
	 * @throws URISyntaxException 
	 */
	@Test
	public void testDockerUserImageLifeCycle() throws URISyntaxException {
		// the name of our docker user
		String userName = "lifecycle";
		String orionBase = "orion-base";
		String userBase = userName + "-base";

		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI, dockerLocationURI, dockerPortStart, dockerPortEnd, dockerUserId, dockerGroupId);

		// make sure docker is running
		DockerVersion dockerVersion = dockerServer.getDockerVersion();
		assertEquals(dockerVersion.getStatusMessage(), DockerResponse.StatusCode.OK, dockerVersion.getStatusCode());
		System.out.println("Docker Server " + dockerLocation + " is running version " + dockerVersion.getVersion());

		// make sure the orion base image exists
		DockerImage dockerImage = dockerServer.getDockerImage(orionBase);
		assertEquals(dockerImage.getStatusMessage(), DockerResponse.StatusCode.OK, dockerImage.getStatusCode());
		System.out.println("Docker Image " + orionBase + " exists");

		// make sure the image for the test user is not there
		dockerImage = dockerServer.getDockerImage(userBase);
		assertEquals(dockerImage.getStatusMessage(), DockerResponse.StatusCode.NO_SUCH_IMAGE, dockerImage.getStatusCode());
		System.out.println("Docker Image " + userBase + " does not exist");

		// create the image for the test user
		dockerImage = dockerServer.createDockerUserBaseImage(userName);
		assertEquals(dockerImage.getStatusMessage(), DockerResponse.StatusCode.CREATED, dockerImage.getStatusCode());
		System.out.println("Docker Image " + dockerImage.getId() + " was created for user " + userName);

	}

	/**
	 * Test get docker container.
	 * @throws URISyntaxException
	 */
	public void testGetDockerContainer() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI, dockerLocationURI, dockerPortStart, dockerPortEnd, dockerUserId, dockerGroupId);
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
		DockerServer dockerServer = new DockerServer(dockerLocationURI, dockerLocationURI, dockerPortStart, dockerPortEnd, dockerUserId, dockerGroupId);
		DockerContainers dockerContainers = dockerServer.getDockerContainers();
		assertEquals(dockerContainers.getStatusMessage(), DockerResponse.StatusCode.OK, dockerContainers.getStatusCode());
		System.out.println("Docker Containers: ");
		for (DockerContainer dockerContainer : dockerContainers.getContainers()) {
			System.out.println("Container Id " + dockerContainer.getId() + " Image " + dockerContainer.getImage() + " Name " + dockerContainer.getName());
		}
	}

	/**
	 * Test get docker image.
	 * @throws URISyntaxException
	 */
	public void testGetDockerImage() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI, dockerLocationURI, dockerPortStart, dockerPortEnd, dockerUserId, dockerGroupId);
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
		DockerServer dockerServer = new DockerServer(dockerLocationURI, dockerLocationURI, dockerPortStart, dockerPortEnd, dockerUserId, dockerGroupId);
		DockerImages dockerImages = dockerServer.getDockerImages();
		assertEquals(dockerImages.getStatusMessage(), DockerResponse.StatusCode.OK, dockerImages.getStatusCode());
		System.out.println("Docker Images: ");
		for (DockerImage dockerImage : dockerImages.getImages()) {
			System.out.println("Repository " + dockerImage.getRepository());
			System.out.print("Tag " + dockerImage.getTag());
			System.out.print("Image Id " + dockerImage.getId());
			System.out.print("Created " + dockerImage.getCreated());
			System.out.println("Size " + dockerImage.getSize());
		}
	}

	/**
	 * Test get docker version, if this test fails docker is likely not running.
	 * @throws URISyntaxException 
	 */
	public void testGetDockerVersion() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI, dockerLocationURI, dockerPortStart, dockerPortEnd, dockerUserId, dockerGroupId);
		DockerVersion dockerVersion = dockerServer.getDockerVersion();
		assertEquals(dockerVersion.getStatusMessage(), DockerResponse.StatusCode.OK, dockerVersion.getStatusCode());
		assertEquals("unknown docker version", "0.6.7", dockerVersion.getVersion());
	}

}
