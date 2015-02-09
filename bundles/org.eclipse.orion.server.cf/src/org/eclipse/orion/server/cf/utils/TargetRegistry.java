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

import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.cf.CFExtServiceHelper;
import org.eclipse.orion.server.cf.commands.SetOrgCommand;
import org.eclipse.orion.server.cf.commands.SetSpaceCommand;
import org.eclipse.orion.server.cf.objects.Cloud;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.json.JSONObject;

public class TargetRegistry {

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

	public Target getTarget(String userId, URL url, String org, String space) {
		UserClouds userClouds = getUserClouds(userId);
		return userClouds.getTarget(url, org, space);
	}

	public Cloud getCloud(String userId, URL url) {
		UserClouds userClouds = getUserClouds(userId);
		return userClouds.getCloud(url);
	}

	public Cloud createTempCloud(URL url) {
		CFExtServiceHelper helper = CFExtServiceHelper.getDefault();
		if (helper != null && helper.getService() != null) {
			Cloud someCloud = helper.getService().getClouds().get(url);
			if (someCloud != null)
				return new DarkCloud(someCloud, null);
		}
		return new DarkCloud(url, null, null);
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
		private Set<Target> targets;

		private Target defaultTarget;

		private UserClouds(String userId) {
			this.userId = userId;
			this.userCloudMap = Collections.synchronizedMap(new HashMap<URL, Cloud>());
			this.targets = new HashSet<Target>();
		}

		private Cloud getCloud(URL url) {
			url = URLUtil.normalizeURL(url);
			if (url == null || (defaultTarget != null && url.equals(defaultTarget.getCloud().getUrl()))) {
				return defaultTarget != null ? defaultTarget.getCloud() : null;
			}

			Cloud cloud = userCloudMap.get(url);
			if (cloud == null) {
				CFExtServiceHelper helper = CFExtServiceHelper.getDefault();
				if (helper != null && helper.getService() != null) {
					Cloud someCloud = helper.getService().getClouds().get(url);
					if (someCloud != null) {
						cloud = new DarkCloud(someCloud, userId);
					}
				}

				if (cloud == null) {
					Cloud someCloud = getConfigCloud();
					if (someCloud != null && someCloud.getUrl().equals(url)) {
						cloud = someCloud;
					} else {
						cloud = new DarkCloud(url, null, this.userId);
					}
				}

				userCloudMap.put(cloud.getUrl(), cloud);
			}
			setAuthToken(cloud);
			return cloud;
		}

		private Cloud getConfigCloud() {
			try {
				String cloudConf = OrionConfiguration.getMetaStore().readUser(userId).getProperties().get("cm/configurations/org.eclipse.orion.client.cf.settings");
				JSONObject cloudConfJSON = new JSONObject(cloudConf);
				URL cloudUrl = URLUtil.normalizeURL(new URL(cloudConfJSON.getString("targetUrl")));
				return new DarkCloud(cloudUrl, URLUtil.normalizeURL(new URL(cloudConfJSON.getString("manageUrl"))), userId);
			} catch (Exception e) {
				return null;
			}
		}

		private Target getTarget(URL url) {
			url = URLUtil.normalizeURL(url);
			if (url == null || (defaultTarget != null && url.equals(defaultTarget.getCloud().getUrl()))) {
				return defaultTarget;
			}
			Cloud cloud = getCloud(url);
			return new Target(cloud);
		}

		private Target getTarget(URL url, String org, String space) {
			if (url == null || org == null || space == null) {
				return null;
			}
			url = URLUtil.normalizeURL(url);

			synchronized (targets) {
				for (Target t : targets) {
					if (org.equals(t.getOrg().getName()) && space.equals(t.getSpace().getName()) && url.equals(t.getUrl())) {
						return new Target(t.getCloud(), t.getOrg(), t.getSpace());
					}
				}
			}

			Cloud cloud = getCloud(url);
			Target target = new Target(cloud);
			IStatus result = new SetOrgCommand(target, org).doIt();
			if (!result.isOK())
				return null;

			result = new SetSpaceCommand(target, space).doIt();
			if (!result.isOK())
				return null;

			synchronized (targets) {
				targets.add(target);
			}

			return target;
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
	}

	private class DarkCloud extends Cloud {
		protected DarkCloud(URL apiUrl, URL manageUrl, String userId) {
			super(apiUrl, manageUrl, userId);
		}

		protected DarkCloud(Cloud cloud, String userId) {
			super(cloud.getUrl(), cloud.getManageUrl(), userId);
		}
	}
}
