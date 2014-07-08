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
package org.eclipse.orion.server.cf.service;

import org.eclipse.core.filesystem.IFileStore;

public interface IDeploymentService {

	public String getType(IFileStore application);

	public DeploymentDescription getDeploymentDescription(String applicationName, IFileStore application);
}
