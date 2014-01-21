/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.tests.servlets.users.UsersTest;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Measure the performance of the user storage.
 * 
 * @author Anthony Hunter
 */
public class SimpleServerUserStressTest extends UsersTest {

	@Override
	public void setAdminRights(User adminUser) throws CoreException {
		//by default allow 'admin' to modify all users data
		AuthorizationService.addUserRight(adminUser.getUid(), "/users");
		AuthorizationService.addUserRight(adminUser.getUid(), "/users/*");
	}

	@Override
	public void setTestUserRights(User testUser) throws CoreException {
		//by default allow 'test' to modify his own data
		AuthorizationService.addUserRight(testUser.getUid(), "/users/" + testUser.getUid());
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
	public void testCreateUsers() throws IOException, SAXException {
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
			Map<String, String> params = new HashMap<String, String>();
			String login = getRandomName();
			params.put("login", login);
			params.put("Name", getRandomName() + " " + getRandomName());
			params.put("email", login + "@example.com");
			params.put("password", getRandomName());

			WebRequest request = getPostUsersRequest("", params, true);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(response.getText(), HttpURLConnection.HTTP_OK, response.getResponseCode());

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
