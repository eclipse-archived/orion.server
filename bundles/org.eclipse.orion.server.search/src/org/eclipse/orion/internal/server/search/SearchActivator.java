/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search;

import org.eclipse.core.runtime.jobs.Job;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class SearchActivator implements BundleActivator {
	private static BundleContext context;

	private static SearchActivator instance;
	public static final String PI_SEARCH = "org.eclipse.orion.server.core.search"; //$NON-NLS-1$

	static BundleContext getContext() {
		return context;
	}

	public static SearchActivator getInstance() {
		return instance;
	}

	public SearchActivator() {
		super();
		instance = this;
	}

	public void start(BundleContext bundleContext) throws Exception {
		SearchActivator.context = bundleContext;
	}

	public void stop(BundleContext bundleContext) throws Exception {
		// cancel all the running search jobs
		Job.getJobManager().cancel(SearchJob.FAMILY);
		Job.getJobManager().join(SearchJob.FAMILY, null);
	}

}
