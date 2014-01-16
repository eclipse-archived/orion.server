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

public class AbstractCFCommand implements ICFCommand {

	@Override
	public IStatus doIt() {
		return validateParams();
	}

	@Override
	public IStatus undoIt() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IStatus redoIt() {
		// TODO Auto-generated method stub
		return null;
	}

	protected IStatus validateParams() {
		return Status.OK_STATUS;
	}
}
