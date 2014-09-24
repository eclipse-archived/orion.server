/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git;

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.orion.server.core.IWebResourceDecorator;
import org.eclipse.orion.server.git.jobs.GitJob;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class GitActivator implements BundleActivator {

	// The plug-in ID
	public static final String PI_GIT = "org.eclipse.orion.server.git"; //$NON-NLS-1$

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext )
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		context.registerService(IWebResourceDecorator.class, new GitFileDecorator(), null);
		SshSessionFactory.setInstance(new GitSshSessionFactory());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
		Job.getJobManager().cancel(GitJob.FAMILY);
		// TODO might have to use something to cancel this join
		Job.getJobManager().join(GitJob.FAMILY, null);
	}
}
