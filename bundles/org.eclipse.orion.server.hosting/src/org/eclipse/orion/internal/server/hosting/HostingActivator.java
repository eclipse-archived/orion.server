package org.eclipse.orion.internal.server.hosting;

import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class HostingActivator implements BundleActivator {

	public static final String PI_SERVER_HOSTING = "org.eclipse.orion.server.hosting"; //$NON-NLS-1$
	
	private static BundleContext bundleContext;


	private ServiceRegistration<ISiteHostingService> siteHostingRegistration;

	static BundleContext getContext() {
		return bundleContext;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		HostingActivator.bundleContext = bundleContext;
		registerHostingService();
		
		// TODO register IWebResourceDecorator
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		HostingActivator.bundleContext = null;
		unregisterHostingService();
	}
	
	private void registerHostingService() {
		siteHostingRegistration = bundleContext.registerService(ISiteHostingService.class, new SiteHostingService(), null);
	}

	private void unregisterHostingService() {
		if (siteHostingRegistration != null) {
			siteHostingRegistration.unregister();
			siteHostingRegistration = null;
		}
	}

}
