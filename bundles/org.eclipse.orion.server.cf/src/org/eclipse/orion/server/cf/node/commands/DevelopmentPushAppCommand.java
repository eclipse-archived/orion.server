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
package org.eclipse.orion.server.cf.node.commands;

import org.eclipse.orion.server.cf.commands.PushAppCommand;
import org.eclipse.orion.server.cf.commands.UploadBitsCommand;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;

public class DevelopmentPushAppCommand extends PushAppCommand {

	private App app;
	private String password;
	private String urlPrefix;

	public DevelopmentPushAppCommand(Target target, App app, boolean reset, String password, String urlPrefix) {
		super(target, app, reset);
		this.app = app;
		this.password = password;
		this.urlPrefix = urlPrefix;
	}

	@Override
	protected UploadBitsCommand createUploadBitsCommand() {
		/* inject debug support */
		return new InjectDebugAndUploadBitsCommand(target, app, password, urlPrefix);
	}
}
