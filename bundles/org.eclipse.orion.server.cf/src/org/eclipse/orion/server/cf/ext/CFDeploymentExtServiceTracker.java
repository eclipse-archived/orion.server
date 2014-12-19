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

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public final class CFDeploymentExtServiceTracker implements ServiceTrackerCustomizer<CFDeploymentExtService, ICFDeploymentExtService> {

	private final BundleContext context;

	public CFDeploymentExtServiceTracker(BundleContext context) {
		this.context = context;
	}

	@Override
	public ICFDeploymentExtService addingService(ServiceReference<CFDeploymentExtService> reference) {
		return context.getService(reference);
	}

	@Override
	public void modifiedService(ServiceReference<CFDeploymentExtService> reference, ICFDeploymentExtService service) {

		/* replace the service */
		removedService(reference, service);
		addingService(reference);
	}

	@Override
	public void removedService(ServiceReference<CFDeploymentExtService> reference, ICFDeploymentExtService service) {
		context.ungetService(reference);
	}
}
