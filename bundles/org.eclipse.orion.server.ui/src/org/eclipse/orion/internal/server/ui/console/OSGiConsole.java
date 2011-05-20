/*******************************************************************************
 * Copyright (c) 2010 Red Hat and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Chris Aniszczyk <caniszczyk@gmail.com> - initial API and implementation
 *     IBM Corporation - ongoing enhancements and bug fixing
 *******************************************************************************/
package org.eclipse.orion.internal.server.ui.console;

import org.eclipse.orion.internal.server.ui.Activator;

import java.io.InputStream;
import java.io.OutputStream;
import org.eclipse.osgi.framework.console.ConsoleSession;
import org.eclipse.ui.console.IOConsole;
import org.osgi.framework.BundleContext;

/**
 * OSGi console connected to the Host/Running Eclipse.
 * 
 * @since 3.6
 */
public class OSGiConsole extends IOConsole {

	public final static String TYPE = "osgiConsole"; //$NON-NLS-1$

	private final ConsoleSession session;

	public OSGiConsole(final OSGiConsoleFactory factory) {
		super("", TYPE, null, true);
		session = new ConsoleSession() {

			public OutputStream getOutput() {
				return newOutputStream();
			}

			public InputStream getInput() {
				return getInputStream();
			}

			protected void doClose() {
				factory.closeConsole(OSGiConsole.this);
			}
		};
	}

	protected void init() {
		super.init();

		BundleContext context = Activator.getDefault().getBundle().getBundleContext();
		context.registerService(ConsoleSession.class.getName(), session, null);
	}

	protected void dispose() {
		super.dispose();
	}

}
