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

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.cf.CFExtServiceHelper;
import org.eclipse.orion.server.cf.objects.Cloud;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCFCommand implements ICFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	protected Target target;
	private Cloud cloud;
	private boolean wasRun = false;

	protected AbstractCFCommand(Target target) {
		this.target = target;
	}

	protected AbstractCFCommand(Cloud cloud) {
		this.cloud = cloud;
	}

	public Cloud getCloud() {
		if (target != null)
			return target.getCloud();
		return cloud;
	}

	public Target getTarget() {
		return target;
	}

	@Override
	public IStatus doIt() {
		long time = System.currentTimeMillis();
		IStatus status = validateParams();
		if (!status.isOK())
			return status;

		ServerStatus doItStatus = this._doIt();
		ServerStatus result = retryIfNeeded(doItStatus);
		logger.debug(getClass() + " took " + (System.currentTimeMillis() - time));
		wasRun = true;
		return result;
	}

	protected ServerStatus retryIfNeeded(ServerStatus doItStatus) {
		if (getCloud() == null)
			return doItStatus;

		CFExtServiceHelper helper = CFExtServiceHelper.getDefault();
		if (doItStatus.getHttpCode() == 401 && helper != null && helper.getService() != null) {
			getCloud().setAccessToken(helper.getService().getToken(getCloud()));
			return _doIt();
		}
		return doItStatus;
	}

	protected abstract ServerStatus _doIt();

	protected void assertWasRun() {
		Assert.isTrue(wasRun);
	}

	protected IStatus validateParams() {
		return Status.OK_STATUS;
	}
}
