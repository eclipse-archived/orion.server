/*******************************************************************************
 * Copyright (c) 2007, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core;

import java.util.ArrayList;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.Activator;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.framework.log.FrameworkLogEntry;

public class LogHelper {

	/**
	 * Copied from PlatformLogWriter in core runtime.
	 */
	private static FrameworkLogEntry getLog(IStatus status) {
		Throwable t = status.getException();
		ArrayList<FrameworkLogEntry> childlist = new ArrayList<FrameworkLogEntry>();

		int stackCode = t instanceof CoreException ? 1 : 0;
		// ensure a substatus inside a CoreException is properly logged 
		if (stackCode == 1) {
			IStatus coreStatus = ((CoreException) t).getStatus();
			if (coreStatus != null) {
				childlist.add(getLog(coreStatus));
			}
		}

		if (status.isMultiStatus()) {
			IStatus[] children = status.getChildren();
			for (int i = 0; i < children.length; i++) {
				childlist.add(getLog(children[i]));
			}
		}

		FrameworkLogEntry[] children = (childlist.size() == 0 ? null : childlist.toArray(new FrameworkLogEntry[childlist.size()]));

		return new FrameworkLogEntry(status.getPlugin(), status.getSeverity(), status.getCode(), status.getMessage(), stackCode, t, children);
	}
	
	public static void log(IStatus status) {
		FrameworkLog log = Activator.getFrameworkLog();
		if (log != null) {
			log.log(getLog(status));
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
