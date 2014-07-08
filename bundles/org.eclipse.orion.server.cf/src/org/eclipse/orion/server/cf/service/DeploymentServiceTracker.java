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

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class DeploymentServiceTracker implements ServiceTrackerCustomizer<DeploymentService, IDeploymentService> {

	private BundleContext context;

	public DeploymentServiceTracker(BundleContext context) {
		this.context = context;
	}

	@Override
	public IDeploymentService addingService(ServiceReference<DeploymentService> reference) {
		IDeploymentService deploymentService = context.getService(reference);
		return deploymentService;
	}

	@Override
	public void modifiedService(ServiceReference<DeploymentService> reference, IDeploymentService service) {
		/* replace the service */
		removedService(reference, service);
		addingService(reference);
	}

	@Override
	public void removedService(ServiceReference<DeploymentService> reference, IDeploymentService service) {
		context.ungetService(reference);
	}
}