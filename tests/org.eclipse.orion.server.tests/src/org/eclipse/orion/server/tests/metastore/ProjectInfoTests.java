package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.junit.Test;

public class ProjectInfoTests {

	@Test
	public void testUniqueId() {
		ProjectInfo projectInfo = new ProjectInfo();
		String id = "id";
		projectInfo.setUniqueId(id);
		assertEquals(id, projectInfo.getUniqueId());
	}

	@Test
	public void testFullName() {
		ProjectInfo projectInfo = new ProjectInfo();
		String fullName = "Test Project";
		projectInfo.setFullName(fullName);
		assertEquals(fullName, projectInfo.getFullName());
	}

	@Test
	public void testContentLocation() throws URISyntaxException {
		ProjectInfo projectInfo = new ProjectInfo();
		String contentLocation = "file:/home/test/james/root";
		projectInfo.setContentLocation(new URI(contentLocation));
		assertEquals(contentLocation, projectInfo.getContentLocation().toString());
	}

	@Test
	public void testProperties() {
		ProjectInfo projectInfo = new ProjectInfo();
		String key1 = "key1";
		String value1 = "value1";
		String key2 = "key2";
		String value2 = "value2";
		assertNull(projectInfo.getProperty(key1));
		assertNull(projectInfo.getProperty(key2));
		assertEquals(0, projectInfo.getProperties().size());
		projectInfo.setProperty(key1, value1);
		assertEquals(value1, projectInfo.getProperty(key1));
		assertEquals(1, projectInfo.getProperties().size());
		projectInfo.setProperty(key2, value2);
		assertEquals(value1, projectInfo.getProperty(key1));
		assertEquals(value2, projectInfo.getProperty(key2));
		assertEquals(2, projectInfo.getProperties().size());
		projectInfo.setProperty(key2, null);
		assertNull(projectInfo.getProperty(key2));
		assertEquals(1, projectInfo.getProperties().size());
		projectInfo.setProperty(key1, null);
		assertNull(projectInfo.getProperty(key1));
		assertEquals(0, projectInfo.getProperties().size());
	}

}
