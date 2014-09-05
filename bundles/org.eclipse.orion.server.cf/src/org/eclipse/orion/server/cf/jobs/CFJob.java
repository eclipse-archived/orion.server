/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.jobs;

import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;

import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.core.tasks.TaskJob;

public abstract class CFJob extends TaskJob {

	protected final String userId;
	protected final String requestLocation;

	@Override
	public synchronized TaskInfo startTask() {
		TaskInfo task = super.startTask();
		task.setUnqualificationStrategy(JsonURIUnqualificationStrategy.LOCATION_ONLY);
		return task;
	}

	public CFJob(HttpServletRequest req, boolean keep) {
		super(req.getRemoteUser(), keep);
		this.userId = req.getRemoteUser();
		this.requestLocation = req.getRequestURI();
		setTaskExpirationTime(TimeUnit.DAYS.toMillis(1));
	}
}