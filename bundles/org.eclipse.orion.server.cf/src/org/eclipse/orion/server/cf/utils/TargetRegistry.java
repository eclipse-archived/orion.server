/*******************************************************************************
 * Copyright (c) 2013, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.utils;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.orion.server.cf.CFExtServiceHelper;
import org.eclipse.orion.server.cf.objects.Cloud;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TargetRegistry {
	
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private Map<String, UserClouds> cloudMap;

	public TargetRegistry() {
		this.cloudMap = Collections.synchronizedMap(new HashMap<String, UserClouds>());
	}

	public Target getDefaultTarget(String userId) {
		return this.getTarget(userId, null);
	}

	public Target getTarget(String userId, URL url) {
		UserClouds userClouds = getUserClouds(userId);
		return userClouds.getTarget(url);
	}

	public Cloud getCloud(String userId, URL url) {
		UserClouds userClouds = getUserClouds(userId);
		return userClouds.getCloud(url);
	}

	public void setDefaultTarget(String userId, Target target) {
		UserClouds userClouds = getUserClouds(userId);
		userClouds.setDefaulTarget(target);
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
		private Target defaultTarget;

		private UserClouds(String userId) {
			this.userId = userId;
			this.userCloudMap = Collections.synchronizedMap(new HashMap<URL, Cloud>());
		}

		private Cloud getCloud(URL url) {
			logger.debug("UserClouds: " + getInfo());
			
			url = URLUtil.normalizeURL(url);
			if (url == null || (defaultTarget != null && url.equals(defaultTarget.getCloud().getUrl()))) {
				return defaultTarget != null ? defaultTarget.getCloud() : null;
			}

			Cloud cloud = userCloudMap.get(url);
			if (cloud == null) {
				CFExtServiceHelper helper = CFExtServiceHelper.getDefault();
				if (helper != null && helper.getService() != null) {
					cloud = helper.getService().getCloud(userId, url);
				} else {
					// default cloud creation
					cloud = new DarkCloud(null, url, null, this.userId);
				}

				if (cloud == null) {
					Cloud someCloud = getConfigCloud();
					if (someCloud != null && someCloud.getUrl().equals(url)) {
						cloud = someCloud;
					} else {
						cloud = new DarkCloud(null, url, null, this.userId);
					}
				}

				userCloudMap.put(url, cloud);
			}
			setAuthToken(cloud);
			return cloud;
		}

		private Cloud getConfigCloud() {
			try {
				String cloudConf = OrionConfiguration.getMetaStore().readUser(userId).getProperties()
						.get("cm/configurations/org.eclipse.orion.client.cf.settings");
				JSONObject cloudConfJSON = new JSONObject(cloudConf);
				URL cloudUrl = URLUtil.normalizeURL(new URL(cloudConfJSON.getString("targetUrl")));
				return new DarkCloud(null, cloudUrl, URLUtil.normalizeURL(new URL(cloudConfJSON.getString("manageUrl"))), userId);
			} catch (Exception e) {
				return null;
			}
		}

		private Target getTarget(URL url) {
			url = URLUtil.normalizeURL(url);
			if (url == null) {
				return defaultTarget;
			}

			if (defaultTarget != null && url.equals(defaultTarget.getCloud().getUrl())) {
				return new Target(defaultTarget.getCloud());
			}

			Cloud cloud = getCloud(url);
			return new Target(cloud);
		}

		private void setDefaulTarget(Target target) {
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

		private String getInfo() {
			StringBuffer buf = new StringBuffer();
			buf.append("userId: " + this.userId + "\n");
			buf.append("clouds: " + this.userCloudMap.size() + "\n");
			for (Iterator<Cloud> iterator = this.userCloudMap.values().iterator(); iterator.hasNext();) {
				Cloud cloud = iterator.next();
				buf.append(cloud.getRegion() + "(" + cloud.getUrl().toString() + ")" + "\n");
			}
			return buf.toString();
		}
	}

	private class DarkCloud extends Cloud {
		protected DarkCloud(String regionId, URL apiUrl, URL manageUrl, String userId) {
			super(regionId, apiUrl, manageUrl, userId);
		}
	}
}
