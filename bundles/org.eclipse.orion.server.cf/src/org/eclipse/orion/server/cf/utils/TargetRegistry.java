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

	private Map<String, UserTargets> cloudMap;

	public TargetRegistry() {
		this.cloudMap = Collections.synchronizedMap(new HashMap<String, UserTargets>());
	}

	public Target getTarget(String userId) {
		return this.getTarget(userId, null);
	}

	public Target getTarget(String userId, URL url) {
		UserTargets userClouds = getUserClouds(userId);
		return userClouds.get(url);
	}

	public void markDefault(String userId, Target target) {
		UserTargets userClouds = getUserClouds(userId);
		userClouds.markDefault(target);
	}

	private UserTargets getUserClouds(String userId) {
		UserTargets userClouds = cloudMap.get(userId);
		if (userClouds == null) {
			userClouds = new UserTargets(userId);
			cloudMap.put(userId, userClouds);
		}
		return userClouds;
	}

	private class UserTargets {

		private String userId;

		private Map<URL, Cloud> userCloudMap;

		private Target defaultTarget;

		private UserTargets(String userId) {
			this.userId = userId;
			this.userCloudMap = Collections.synchronizedMap(new HashMap<URL, Cloud>());
		}

		private Cloud getCloud(URL url) {
			Cloud cloud = userCloudMap.get(url);
			if (cloud == null) {
				cloud = new Cloud(url, null, this.userId);
				userCloudMap.put(cloud.getUrl(), cloud);
			}
			setAuthToken(cloud);
			return cloud;
		}

		private Target get(URL url) {
			url = normalizeURL(url);
			if (url == null || (defaultTarget != null && url.equals(defaultTarget.getCloud().getUrl()))) {
				return defaultTarget;
			}
			Cloud cloud = getCloud(url);
			return new Target(cloud);
		}

		private void markDefault(Target target) {
			Cloud cloud = getCloud(target.getCloud().getUrl());
			Target newTarget = new Target(cloud);
			newTarget.setOrg(target.getOrg());
			newTarget.setSpace(target.getSpace());
			defaultTarget = newTarget;
		}

		private void setAuthToken(Cloud cloud) {
			CFExtServiceHelper helper = CFExtServiceHelper.getDefault();
			if (cloud.getAccessToken() == null && helper != null && helper.getService() != null) {
				cloud.setAccessToken(helper.getService().getToken(cloud));
			}
		}

		private URL normalizeURL(URL url) {
			if (url == null)
				return null;

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
