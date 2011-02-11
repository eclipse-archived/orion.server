package org.eclipse.orion.internal.server.hosting;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.eclipse.orion.internal.server.servlets.site.SiteConfiguration;
import org.eclipse.orion.server.core.LogHelper;

public class SiteHostingService implements ISiteHostingService {

	@Override
	public void start(SiteConfiguration siteConfig) {
		// FIXME mamacdon implement
		LogHelper.log(new Status(IStatus.ERROR, HostingActivator.PI_SERVER_HOSTING, "Finish the hosting service!"));
	}

	@Override
	public void stop(SiteConfiguration siteConfig) {
		// FIXME mamacdon implement
		LogHelper.log(new Status(IStatus.ERROR, HostingActivator.PI_SERVER_HOSTING, "Finish the hosting service!"));
	}

	@Override
	public boolean isRunning(SiteConfiguration siteConfig) {
		// FIXME mamacdon implement
		LogHelper.log(new Status(IStatus.ERROR, HostingActivator.PI_SERVER_HOSTING, "Finish the hosting service!"));
		return false;
	}

}
