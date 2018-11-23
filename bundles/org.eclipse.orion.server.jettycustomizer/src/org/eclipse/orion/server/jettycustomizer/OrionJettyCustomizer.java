/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.jettycustomizer;

import java.io.File;
import java.util.Dictionary;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.equinox.http.jetty.JettyCustomizer;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configure Jetty to produce request logs of the server in NCSA format.
 * 
 * @author Anthony Hunter
 */
public final class OrionJettyCustomizer extends JettyCustomizer {

	@Override
	public Object customizeContext(Object context, Dictionary<String, ?> settings) {
		if (context instanceof ServletContextHandler) {
			ServletContextHandler jettyContext = (ServletContextHandler) context;

			IFileStore fileStore = OrionConfiguration.getRootLocation();
			File rootLocation = null;
			try {
				rootLocation = fileStore.toLocalFile(EFS.NONE, null);
			} catch (CoreException e) {
				Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
				logger.error("Could not initialize NCSA Request Log", e); //$NON-NLS-1$
			}

			File metadata = new File(rootLocation, ".metadata");
			if (!metadata.exists() && !metadata.isDirectory()) {
				Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
				logger.error("Could not initialize NCSA Request Log: Folder does not exist: " + metadata.toString()); //$NON-NLS-1$
			}

			File logsFolder = new File(metadata, "access_logs");
			if (!logsFolder.exists()) {
				logsFolder.mkdir();
				if (!logsFolder.exists() && !logsFolder.isDirectory()) {
					Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
					logger.error("Could not initialize NCSA Request Log: Logs folder does not exist: " + logsFolder.toString()); //$NON-NLS-1$
				}
			}

			NCSARequestLog requestLog = new NCSARequestLog(logsFolder.toString() + File.separator + "orion-access-yyyy_mm_dd.log");
			requestLog.setRetainDays(90);
			requestLog.setAppend(true);
			requestLog.setExtended(true);
			requestLog.setLogTimeZone("EST");

			RequestLogHandler requestLogHandler = new RequestLogHandler();
			requestLogHandler.setRequestLog(requestLog);

			jettyContext.setHandler(requestLogHandler);
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.info("Initialized NCSA Request Logs in " + logsFolder.toString()); //$NON-NLS-1$
		}
		return super.customizeContext(context, settings);
	}
}
