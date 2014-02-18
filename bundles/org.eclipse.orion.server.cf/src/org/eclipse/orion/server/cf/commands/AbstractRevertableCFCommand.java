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

import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;

public abstract class AbstractRevertableCFCommand extends AbstractCFCommand {

	protected App application;

	protected AbstractRevertableCFCommand(Target target, App application) {
		super(target);
		this.application = application;
	}

	protected ServerStatus revert(ServerStatus status) {
		DeleteApplicationCommand deleteApplicationCommand = new DeleteApplicationCommand(target, application);
		deleteApplicationCommand.doIt(); /* we don't need to know whether the deletion succeeded or not */

		DeleteApplicationRoutesCommand deleteRouteCommand = new DeleteApplicationRoutesCommand(target, application);
		deleteRouteCommand.doIt(); /* we don't need to know whether the deletion succeeded or not */

		return status;
	}
}
