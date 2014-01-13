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
import org.eclipse.orion.server.cf.CFAuthServiceHelper;
import org.eclipse.orion.server.cf.objects.Target;

public class TargetRegistry {

	private Map<String, UserTargets> targetMap;

	public TargetRegistry() {
		this.targetMap = Collections.synchronizedMap(new HashMap<String, UserTargets>());
	}

	public Target getTarget(String userId) {
		return this.getTarget(userId, null);
	}

	public Target getTarget(String userId, URL url) {
		UserTargets userTargets = getUserTargets(userId);
		return userTargets.get(url);
	}

	public void putTarget(String userId, Target target) {
		this.putTarget(userId, target, false);
	}

	public void putTarget(String userId, Target target, boolean isDefault) {
		UserTargets userTargets = getUserTargets(userId);
		userTargets.put(target, isDefault);
	}

	private UserTargets getUserTargets(String userId) {
		UserTargets userTargets = targetMap.get(userId);
		if (userTargets == null) {
			userTargets = new UserTargets(userId);
			targetMap.put(userId, userTargets);
		}
		return userTargets;
	}

	private class UserTargets {

		private String userId;

		private Map<URL, Target> userTargetMap;

		private URL defaultTargetUrl;

		UserTargets(String userId) {
			this.userId = userId;
			this.userTargetMap = Collections.synchronizedMap(new HashMap<URL, Target>());
		}

		Target get(URL url) {
			url = (url != null ? url : defaultTargetUrl);
			if (url == null) {
				return null;
			}
			Target target = userTargetMap.get(url);
			if (target == null) {
				target = new Target();
				target.setUrl(url);
				userTargetMap.put(target.getUrl(), target);
			}
			setAuthToken(target);
			return new Target(target);
		}

		void put(Target target, boolean isDefault) {
			userTargetMap.put(target.getUrl(), target);
			if (isDefault)
				defaultTargetUrl = target.getUrl();
		}

		private void setAuthToken(Target target) {
			CFAuthServiceHelper helper = CFAuthServiceHelper.getDefault();
			if (target.getAccessToken() == null && helper != null && helper.getService() != null) {
				target.setAccessToken(helper.getService().getToken(userId, target));
			}
		}
	}
}
