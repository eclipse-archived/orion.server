package org.eclipse.orion.internal.server.hosting;

import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class HostingActivator implements BundleActivator {

	public static final String PI_SERVER_HOSTING = "org.eclipse.orion.server.hosting"; //$NON-NLS-1$
	
	private static BundleContext bundleContext;
	private static HostingActivator singleton;

	private SiteHostingService siteHostingService;
	private ServiceRegistration<ISiteHostingService> siteHostingRegistration;
	private ServiceRegistration<IWebResourceDecorator> hostedStatusDecoratorRegistration;

	static BundleContext getContext() {
		return bundleContext;
	}
	
	public static HostingActivator getDefault() {
		return singleton;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		singleton = this;
		HostingActivator.bundleContext = bundleContext;
		registerHostingService();
		registerDecorators();
	}

	private void registerHostingService() {
		int port = Integer.parseInt(System.getProperty("org.eclipse.equinox.http.jetty.http.port")); //$NON-NLS-1$
		siteHostingService = new SiteHostingService(port);
		siteHostingRegistration = bundleContext.registerService(ISiteHostingService.class, siteHostingService, null);
	}
	
	private void registerDecorators() {
		hostedStatusDecoratorRegistration = bundleContext.registerService(IWebResourceDecorator.class, new HostedStatusDecorator(), null);
	}
	
	SiteHostingService getHostingService() {
		return siteHostingService;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		HostingActivator.bundleContext = null;
		unregisterHostingService();
		unregisterDecorators();
		siteHostingService = null;
	}

	private void unregisterHostingService() {
		if (siteHostingRegistration != null) {
			siteHostingRegistration.unregister();
			siteHostingRegistration = null;
		}
	}

	private void unregisterDecorators() {
		if (hostedStatusDecoratorRegistration != null) {
			hostedStatusDecoratorRegistration.unregister();
			hostedStatusDecoratorRegistration = null;
		}
	}

}
