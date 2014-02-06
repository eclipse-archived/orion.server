/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import org.eclipse.orion.server.cf.CFExtServiceHelper;
import org.eclipse.orion.server.cf.objects.Cloud;
import org.eclipse.orion.server.cf.objects.Target;

public class TargetRegistry {

	private Map<String, UserClouds> cloudMap;

	public TargetRegistry() {
		this.cloudMap = Collections.synchronizedMap(new HashMap<String, UserClouds>());
	}

	public Target getTarget(String userId) {
		return this.getTarget(userId, null);
	}

	public Target getTarget(String userId, URL url) {
		UserClouds userClouds = getUserClouds(userId);
		Cloud cloud = userClouds.get(url);
		if (cloud == null)
			return null;
		return new Target(userClouds.get(url));
	}

	public void markDefault(String userId, Cloud cloud) {
		UserClouds userClouds = getUserClouds(userId);
		userClouds.markDefault(cloud);
	}

	private UserClouds getUserClouds(String userId) {
		UserClouds userClouds = cloudMap.get(userId);
		if (userClouds == null) {
			userClouds = new UserClouds(userId);
			cloudMap.put(userId, userClouds);
		}
		return userClouds;
	}

	private class UserClouds {

		private String userId;

		private Map<URL, Cloud> userCloudMap;

		private URL defaultCloudUrl;

		UserClouds(String userId) {
			this.userId = userId;
			this.userCloudMap = Collections.synchronizedMap(new HashMap<URL, Cloud>());
		}

		Cloud get(URL url) {
			url = (url != null ? normalizeURL(url) : defaultCloudUrl);
			if (url == null) {
				return null;
			}
			Cloud cloud = userCloudMap.get(url);
			if (cloud == null) {
				cloud = new Cloud(url, null, this.userId);
				userCloudMap.put(cloud.getUrl(), cloud);
			}
			setAuthToken(cloud);
			return cloud;
		}

		private void markDefault(Cloud cloud) {
			userCloudMap.get(cloud.getUrl());
			defaultCloudUrl = cloud.getUrl();
		}

		private void setAuthToken(Cloud cloud) {
			CFExtServiceHelper helper = CFExtServiceHelper.getDefault();
			if (cloud.getAccessToken() == null && helper != null && helper.getService() != null) {
				cloud.setAccessToken(helper.getService().getToken(cloud));
			}
		}

		private URL normalizeURL(URL url) {
			String urlString = url.toString();
			if (urlString.endsWith("/")) {
				urlString = urlString.substring(0, urlString.length() - 1);
			}

			try {
				return new URL(urlString);
			} catch (MalformedURLException e) {
				return null;
			}
		}
	}
}
