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

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.cf.CFExtServiceHelper;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;

public abstract class AbstractCFMultiCommand implements ICFCommand {

	protected Target target;
	protected String userId;

	protected AbstractCFMultiCommand(Target target, String userId) {
		this.target = target;
		this.userId = userId;
	}

	@Override
	public IStatus doIt() {
		IStatus status = validateParams();
		if (!status.isOK())
			return status;

		MultiServerStatus doItStatus = this._doIt();
		return retryIfNeeded(doItStatus);
	}

	private MultiServerStatus retryIfNeeded(MultiServerStatus doItStatus) {
		CFExtServiceHelper helper = CFExtServiceHelper.getDefault();
		if (doItStatus.getHttpCode() == 401 && target.getAccessToken() != null && helper != null && helper.getService() != null) {
			target.setAccessToken(helper.getService().getToken(userId, target));
			return _doIt();
		}
		return doItStatus;
	}

	protected abstract MultiServerStatus _doIt();

	protected IStatus validateParams() {
		return Status.OK_STATUS;
	}
}
