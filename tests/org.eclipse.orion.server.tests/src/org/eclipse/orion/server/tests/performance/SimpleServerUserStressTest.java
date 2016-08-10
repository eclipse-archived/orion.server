/*******************************************************************************
 * Copyright (c) 2014, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Random;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.eclipse.orion.server.tests.servlets.users.UsersTest;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Measure the performance of the user storage. Also can be used to create a large number of users in the metastore.
 * 
 * @author Anthony Hunter
 */
public class SimpleServerUserStressTest extends UsersTest {

	@Override
	public void setAdminRights(UserInfo adminUser) throws CoreException {
		//by default allow 'admin' to modify all users data
		AuthorizationService.addUserRight(adminUser.getUniqueId(), "/users");
		AuthorizationService.addUserRight(adminUser.getUniqueId(), "/users/*");
	}

	@Override
	public void setTestUserRights(UserInfo testUser) throws CoreException {
		//by default allow 'test' to modify his own data
		AuthorizationService.addUserRight(testUser.getUniqueId(), "/users/" + testUser.getUniqueId());
	}

	/**
	 * Get a random string of lower case letters between a length of eight and twelve characters to
	 * use as a unique name. 
	 * @return a string of lower case letters.
	 */
	private String getRandomName() {
		String characters = "abcdefghijklmnopqrstuvxwxyz";
		Random random = new Random();
		int length = 8 + random.nextInt(4);
		String name = new String();
		for (int i = 0; i < length; i++) {
			int next = random.nextInt(characters.length());
			name = name + characters.charAt(next);
		}
		return name;
	}

	@Test
	public void testCreateUsers() throws IOException, SAXException, JSONException {
		WebConversation webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);

		Performance performance = Performance.getDefault();
		PerformanceMeter meter = performance.createPerformanceMeter("SimpleServerUserStressTest#testCreateUsers");
		final int USER_COUNT = 10001;
		meter.start();
		long start = System.currentTimeMillis();
		for (int i = 0; i < USER_COUNT; i++) {

			long current_start = System.currentTimeMillis();

			// create a user
			JSONObject json = new JSONObject();
			String login = getRandomName();
			json.put(UserConstants.USER_NAME, login);
			json.put(UserConstants.FULL_NAME, getRandomName() + " " + getRandomName());
			json.put(UserConstants.EMAIL, login + "@example.com");
			json.put(UserConstants.PASSWORD, getRandomName() + System.currentTimeMillis());

			WebRequest request = getPostUsersRequest("", json, true);
			WebResponse response = webConversation.getResponse(request);
			if (response.getResponseCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
				assertTrue(response.getText(), response.getText().contains("already exists."));
			} else {
				assertEquals(response.getText(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());
			}

			if (i % 1000 == 0) {
				long end = System.currentTimeMillis();
				long avg = (end - start) / (i + 1);
				long current = end - current_start;
				System.out.println("Created user " + i + " in " + current + "ms, average time per user: " + avg + "ms");
			}
		}
		meter.stop();
		meter.commit();
	}

}
