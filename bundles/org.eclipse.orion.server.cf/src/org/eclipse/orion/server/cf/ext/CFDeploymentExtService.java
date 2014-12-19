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
package org.eclipse.orion.server.cf.ext;

/**
 * Service consumer for cloud foundry deployment extensions.
 */
public class CFDeploymentExtService implements ICFDeploymentExtService {

	protected ICFEnvironmentExtService environmentExtService;

	protected synchronized void bindEnvironmentExtService(ICFEnvironmentExtService service) {
		environmentExtService = service;
	}

	protected synchronized void unbindEnvironmentExtService(ICFEnvironmentExtService service) {
		environmentExtService = null;
	}

	@Override
	public ICFEnvironmentExtService getDefaultEnvironmentExtService() {
		return environmentExtService;
	}
}
