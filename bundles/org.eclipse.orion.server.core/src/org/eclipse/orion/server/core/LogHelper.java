/*******************************************************************************
 * Copyright (c) 2007, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogHelper {

	private static final String LOGGER_ID = "org.eclipse.orion.server.core";// see logback.xml
	private static Logger logger = LoggerFactory.getLogger(LOGGER_ID);

	/**
	 * Implements logging of an IStatus object, recursively as needed.
	 */
	private static void doLog(IStatus status) {
		if (status.isOK())
			return;
		Throwable t = status.getException();
		if (status.getSeverity() == IStatus.ERROR) {
			logger.error(status.getMessage(), t);
		} else {
			logger.warn(status.getMessage(), t);
		}

		int stackCode = t instanceof CoreException ? 1 : 0;
		// ensure a substatus inside a CoreException is properly logged
		if (stackCode == 1) {
			IStatus coreStatus = ((CoreException) t).getStatus();
			if (coreStatus != null) {
				doLog(coreStatus);
			}
		}

		if (status.isMultiStatus()) {
			IStatus[] children = status.getChildren();
			for (int i = 0; i < children.length; i++) {
				doLog(children[i]);
			}
		}
	}

	public static void log(IStatus status) {
		if (logger != null) {
			doLog(status);
		} else {
			System.out.println(status.getMessage());
			if (status.getException() != null)
				status.getException().printStackTrace();
		}
	}

	public static void log(Throwable t) {
		log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Internal server error", t)); //$NON-NLS-1$
	}
}
