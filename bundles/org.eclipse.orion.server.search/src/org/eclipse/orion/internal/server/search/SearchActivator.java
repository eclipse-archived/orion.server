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

import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.core.IWebResourceDecorator;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class SearchActivator implements BundleActivator, IWebResourceDecorator {
	private static BundleContext context;

	private static SearchActivator instance;
	public static final String PI_SEARCH = "org.eclipse.orion.server.core.search"; //$NON-NLS-1$
	private ServiceRegistration<IWebResourceDecorator> searchDecoratorRegistration;

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

	public void addAtributesFor(HttpServletRequest request, URI resource, JSONObject representation) {
		String service = request.getServletPath();
		if (!("/file".equals(service) || "/workspace".equals(service))) //$NON-NLS-1$ //$NON-NLS-2$
			return;
		try {
			// we can also augment with a query argument that includes the resource path
			URI result = new URI(resource.getScheme(), resource.getAuthority(), "/filesearch", "q=", null); //$NON-NLS-1$//$NON-NLS-2$
			representation.put(ProtocolConstants.KEY_SEARCH_LOCATION, result);
		} catch (URISyntaxException e) {
			LogHelper.log(e);
		} catch (JSONException e) {
			// key and value are well-formed strings so this should not happen
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext bundleContext) throws Exception {
		SearchActivator.context = bundleContext;
		searchDecoratorRegistration = context.registerService(IWebResourceDecorator.class, this, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		searchDecoratorRegistration.unregister();

		//cancel all the running search jobs
		Job.getJobManager().cancel(SearchJob.FAMILY);
		Job.getJobManager().join(SearchJob.FAMILY, null);
	}

}
