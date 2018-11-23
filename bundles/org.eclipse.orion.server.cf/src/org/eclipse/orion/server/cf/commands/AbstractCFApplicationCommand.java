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

public abstract class AbstractCFApplicationCommand extends AbstractCFCommand {

	private App application;

	protected App getApplication() {
		return application;
	}

	public AbstractCFApplicationCommand(Target target, App application) {
		super(target);
		this.application = application;
	}
}
