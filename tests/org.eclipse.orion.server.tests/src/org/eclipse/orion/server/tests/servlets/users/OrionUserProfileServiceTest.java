/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.users;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.user.profile.IOrionUserProfileConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.user.profile.IOrionUserProfileService;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.junit.Test;

/**
 * Test class to exercise {@link IOrionUserProfileService}. 
 * The goal is to hit all the methods used by the Orion Server.
 * 
 * @author Anthony Hunter
 */
public class OrionUserProfileServiceTest {
	@Test
	public void testGetUserNames() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// particulars for the new user
		String login = "testname";
		String name = "Test Names";
		String email = "testname@ca.ibm.com";
		String password = "DontUsePasswordAsThePassword";

		// create user pattern is to persist the new user in the metadata store first
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			fail("Could not create user in IMetaStore: " + e.getLocalizedMessage());
		}

		// create the new user using the IOrionCredentialsService
		User user = new User(userInfo.getUniqueId(), login, name, password);
		user.setEmail(email);
		User createdUser = orionCredentialsService.createUser(user);
		assertNotNull(createdUser);

		// get the profile node
		IOrionUserProfileService orionUserProfileService = UserServiceHelper.getDefault().getUserProfileService();
		IOrionUserProfileNode userProfileNode = orionUserProfileService.getUserProfileNode(createdUser.getUid(), IOrionUserProfileConstants.GENERAL_PROFILE_PART);

		// set the last login timestamp
		String lastLogin = new Long(System.currentTimeMillis()).toString();
		try {
			userProfileNode.put(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, lastLogin, false);
			userProfileNode.flush();
		} catch (CoreException e) {
			fail("Could not put the user profile node: " + e.getLocalizedMessage());
		}

		// get the profile nodes
		orionUserProfileService = UserServiceHelper.getDefault().getUserProfileService();
		String[] userNames = orionUserProfileService.getUserNames();
		List<String> userNamesList = new ArrayList<String>(Arrays.asList(userNames));
		assertTrue(userNamesList.contains(userInfo.getUniqueId()));

		for (String userName : userNamesList) {
			if (userName.equals(userInfo.getUniqueId())) {
				IOrionUserProfileNode readUserProfileNode = orionUserProfileService.getUserProfileNode(userName, false);
				String[] childrenNames = readUserProfileNode.childrenNames();
				List<String> childrenNamesList = new ArrayList<String>(Arrays.asList(childrenNames));
				assertTrue(childrenNamesList.contains(IOrionUserProfileConstants.GENERAL_PROFILE_PART));
				assertEquals(1, childrenNamesList.size());
				for (String profilePart : childrenNamesList) {
					assertEquals(IOrionUserProfileConstants.GENERAL_PROFILE_PART, profilePart);
					IOrionUserProfileNode profilePartNode = readUserProfileNode.getUserProfileNode(profilePart);
					assertNotNull(profilePartNode);
					try {
						String readLastLogin = profilePartNode.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, null);
						assertEquals(lastLogin, readLastLogin);
					} catch (CoreException e) {
						fail("Could not read the user profile node: " + e.getLocalizedMessage());
					}
				}
			}
		}
	}

	@Test
	public void testGetUserProfileNode() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// particulars for the new user
		String login = "profile";
		String name = "Profile Node";
		String email = "profile@ca.ibm.com";
		String password = "DontUsePasswordAsThePassword";

		// create user pattern is to persist the new user in the metadata store first
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			fail("Could not create user in IMetaStore: " + e.getLocalizedMessage());
		}

		// create the new user using the IOrionCredentialsService
		User user = new User(userInfo.getUniqueId(), login, name, password);
		user.setEmail(email);
		User createdUser = orionCredentialsService.createUser(user);

		// get the profile node
		IOrionUserProfileService orionUserProfileService = UserServiceHelper.getDefault().getUserProfileService();
		IOrionUserProfileNode userProfileNode = orionUserProfileService.getUserProfileNode(createdUser.getUid(), IOrionUserProfileConstants.GENERAL_PROFILE_PART);

		// set the last login timestamp
		String lastLogin = new Long(System.currentTimeMillis()).toString();
		try {
			userProfileNode.put(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, lastLogin, false);
			userProfileNode.flush();
		} catch (CoreException e) {
			fail("Could not put the user profile node: " + e.getLocalizedMessage());
		}

		// read back the profile node
		IOrionUserProfileNode readProfileNode = orionUserProfileService.getUserProfileNode(createdUser.getUid(), IOrionUserProfileConstants.GENERAL_PROFILE_PART);
		try {
			String readLastLogin = readProfileNode.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, null);
			assertNotNull(readLastLogin);
			assertEquals(lastLogin, readLastLogin);
		} catch (CoreException e) {
			fail("Could not read the user profile node: " + e.getLocalizedMessage());
		}
	}

	@Test
	public void testGetUserProfileNodeCreateFalse() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// particulars for the new user
		String login = "enode";
		String name = "Existing Node";
		String email = "enode@ca.ibm.com";
		String password = "DontUsePasswordAsThePassword";

		// create user pattern is to persist the new user in the metadata store first
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			fail("Could not create user in IMetaStore: " + e.getLocalizedMessage());
		}

		// create the new user using the IOrionCredentialsService
		User user = new User(userInfo.getUniqueId(), login, name, password);
		user.setEmail(email);
		User createdUser = orionCredentialsService.createUser(user);

		// get the profile node
		IOrionUserProfileService orionUserProfileService = UserServiceHelper.getDefault().getUserProfileService();
		IOrionUserProfileNode userProfileNode = orionUserProfileService.getUserProfileNode(createdUser.getUid(), false);
		assertNotNull(userProfileNode);
	}

	@Test
	public void testGetUserProfileNodeCreateTrue() {
		// get the orion credentials service
		IOrionCredentialsService orionCredentialsService = UserServiceHelper.getDefault().getUserStore();

		// particulars for the new user
		String login = "cnode";
		String name = "Create Node";
		String email = "cnode@ca.ibm.com";
		String password = "DontUsePasswordAsThePassword";

		// create user pattern is to persist the new user in the metadata store first
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName(login);
		userInfo.setFullName(name);
		try {
			OrionConfiguration.getMetaStore().createUser(userInfo);
		} catch (CoreException e) {
			fail("Could not create user in IMetaStore: " + e.getLocalizedMessage());
		}

		// create the new user using the IOrionCredentialsService
		User user = new User(userInfo.getUniqueId(), login, name, password);
		user.setEmail(email);
		User createdUser = orionCredentialsService.createUser(user);

		// get the profile node
		IOrionUserProfileService orionUserProfileService = UserServiceHelper.getDefault().getUserProfileService();
		IOrionUserProfileNode userProfileNode = orionUserProfileService.getUserProfileNode(createdUser.getUid(), true);

		IOrionUserProfileNode generalProfilePart = userProfileNode.getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);
		try {
			generalProfilePart.put("GitMail", email, false);
			generalProfilePart.put("GitName", name, false);
			generalProfilePart.flush();
		} catch (CoreException e) {
			fail("Could not update the user profile node: " + e.getLocalizedMessage());
		}

		// read back the profile node
		IOrionUserProfileNode readProfileNode = orionUserProfileService.getUserProfileNode(createdUser.getUid(), IOrionUserProfileConstants.GENERAL_PROFILE_PART);
		try {
			String gitMail = readProfileNode.get("GitMail", null);
			assertNotNull(gitMail);
			assertEquals(email, gitMail);
			String gitName = readProfileNode.get("GitName", null);
			assertNotNull(gitName);
			assertEquals(name, gitName);
		} catch (CoreException e) {
			fail("Could not read the user profile node: " + e.getLocalizedMessage());
		}
	}
}
