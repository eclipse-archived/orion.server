package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleLinuxMetaStore;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.junit.Test;

public class SimpleLinuxMetaStoreTests {

	@Test
	public void testCreateUser() throws URISyntaxException, CoreException {
		URI metaStoreRoot = new URI("file:/workspace/foo/");
		SimpleLinuxMetaStore simpleLinuxMetaStore = new SimpleLinuxMetaStore(metaStoreRoot);
		assertNotNull(simpleLinuxMetaStore);
		String userId = "anthony";
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(userId);
		simpleLinuxMetaStore.createUser(userInfo);
		UserInfo readUserInfo = simpleLinuxMetaStore.readUser(userId);
		assertNotNull(readUserInfo);
		assertEquals(readUserInfo.getUniqueId(), userId);
		assertEquals(readUserInfo.getUserName(), userInfo.getUserName());
	}

	@Test
	public void testReadAllUsers() throws URISyntaxException, CoreException {
		URI metaStoreRoot = new URI("file:/workspace/foo/.metadata");
		SimpleLinuxMetaStore simpleLinuxMetaStore = new SimpleLinuxMetaStore(metaStoreRoot);
		assertNotNull(simpleLinuxMetaStore);
		List<String> allUsers = simpleLinuxMetaStore.readAllUsers();
		assertNotNull(allUsers);
	}

}
