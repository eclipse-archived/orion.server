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
import org.eclipse.orion.server.cf.objects.Target;

public class TargetMap {

	private Map<String, UserTargets> targetMap;

	public TargetMap() {
		this.targetMap = Collections.synchronizedMap(new HashMap<String, UserTargets>());
	}

	public Target getTarget(String user) {
		return this.getTarget(user, null);
	}

	public Target getTarget(String user, URL url) {
		UserTargets userTargets = getUserTargets(user);
		return userTargets.get(url);
	}

	public void putTarget(String user, Target target) {
		this.putTarget(user, target, false);
	}

	public void putTarget(String user, Target target, boolean isDefault) {
		UserTargets userTargets = getUserTargets(user);
		userTargets.put(target, isDefault);
	}

	private UserTargets getUserTargets(String user) {
		UserTargets userTargets = targetMap.get(user);
		if (userTargets == null) {
			userTargets = new UserTargets();
			targetMap.put(user, userTargets);
		}
		return userTargets;
	}

	private class UserTargets {

		private Map<URL, Target> userTargetMap;

		private URL defaultTargetUrl;

		UserTargets() {
			this.userTargetMap = Collections.synchronizedMap(new HashMap<URL, Target>());
		}

		Target get(URL url) {
			url = (url != null ? url : defaultTargetUrl);
			Target target = userTargetMap.get(url);
			return target != null ? new Target(target) : null;
		}

		void put(Target target, boolean isDefault) {
			userTargetMap.put(target.getUrl(), target);
			if (isDefault)
				defaultTargetUrl = target.getUrl();
		}
	}
}
