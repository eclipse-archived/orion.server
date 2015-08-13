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
package org.eclipse.orion.server.cf.commands;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.cf.*;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.json.JSONObject;

public class ComputeTargetCommand implements ICFCommand {

	private String userId;
	private JSONObject targetJSON;
	private Target target;
	private static final int CACHE_EXPIRES_MS = 60000 * 30;
	private static final int MAX_CACHE_SIZE = 10000;
	static ExpiryCache<Target> targetCache = new ExpiryCache<Target>(MAX_CACHE_SIZE, CACHE_EXPIRES_MS);

	public ComputeTargetCommand(String userId, JSONObject targetJSON) {
		this.userId = userId;
		this.targetJSON = targetJSON;
	}

	public IStatus doIt() {
		URL targetUrl = null;
		String org = null;
		String space = null;

		if (targetJSON != null) {
			try {
				targetUrl = new URL(targetJSON.getString(CFProtocolConstants.KEY_URL));
				org = targetJSON.optString(CFProtocolConstants.KEY_ORG);
				space = targetJSON.optString(CFProtocolConstants.KEY_SPACE);
			} catch (Exception e) {
				// do nothing
			}
		}

		if (targetUrl != null && org != null && space != null) {
			List<Object> key = Arrays.asList(new Object[] {userId, targetUrl, org, space});
			target = targetCache.get(key);
			if (target != null)
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
		}

		target = CFActivator.getDefault().getTargetRegistry().getTarget(userId, targetUrl);
		if (target == null)
			return HttpUtil.createErrorStatus(IStatus.WARNING, "CF-TargetNotSet", "Target not set");

		IStatus result = new SetOrgCommand(target, org, targetJSON.optBoolean("isGuid")).doIt();
		if (!result.isOK())
			return result;

		result = new SetSpaceCommand(target, space, targetJSON.optBoolean("isGuid")).doIt();
		if (!result.isOK())
			return result;

		List<Object> key = Arrays.asList(new Object[] {userId, target.getUrl(), target.getOrg().getName(), target.getSpace().getName()});
		targetCache.put(key, target);

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}

	public Target getTarget() {
		return this.target;
	}
}
