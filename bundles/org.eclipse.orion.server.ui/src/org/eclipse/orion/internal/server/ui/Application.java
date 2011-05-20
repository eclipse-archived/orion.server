/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.internal.server.ui;

import org.eclipse.orion.internal.server.ui.console.OSGiConsoleFactory;

import java.util.Collection;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.*;
import org.osgi.service.application.*;

/**
 * This class controls all aspects of the application's execution
 */
public class Application implements IApplication {
	private static final String ORION_APP = "org.eclipse.orion.application";

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication#start(org.eclipse.equinox.app.IApplicationContext)
	 */
	public Object start(IApplicationContext context) {

		Display display = PlatformUI.createDisplay();

		display.asyncExec(new Runnable() {
			public void run() {
				launchOrionApplication();
				new OSGiConsoleFactory().openConsole();
			}
		});
		try {
			int returnCode = PlatformUI.createAndRunWorkbench(display, new ApplicationWorkbenchAdvisor());
			if (returnCode == PlatformUI.RETURN_RESTART) {
				return IApplication.EXIT_RESTART;
			}
			return IApplication.EXIT_OK;
		} finally {
			display.dispose();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication#stop()
	 */
	public void stop() {
		if (!PlatformUI.isWorkbenchRunning())
			return;
		final IWorkbench workbench = PlatformUI.getWorkbench();
		final Display display = workbench.getDisplay();
		display.syncExec(new Runnable() {
			public void run() {
				if (!display.isDisposed())
					workbench.close();
			}
		});
	}

	protected ApplicationHandle launchOrionApplication() {
		String filter = "(service.pid=" + ORION_APP + ")"; //$NON-NLS-1$//$NON-NLS-2$
		ApplicationDescriptor descriptor = getService(ApplicationDescriptor.class, filter);
		try {
			return descriptor.launch(null);
		} catch (ApplicationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private <S> S getService(Class<S> clazz, String filter) {
		BundleContext context = Activator.getContext();
		if (context == null)
			return null;

		Collection<ServiceReference<S>> references;
		try {
			references = context.getServiceReferences(clazz, filter);
		} catch (InvalidSyntaxException e) {
			// TODO Auto-generated catch block
			return null;
		}
		if (references == null || references.size() == 0)
			return null;

		ServiceReference<S> ref = references.iterator().next();
		S result = context.getService(ref);
		context.ungetService(ref);
		return result;
	}
}
