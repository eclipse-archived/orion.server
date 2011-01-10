package org.eclipse.e4.internal.webide.server.servlets;
/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/


import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * A status that also incorporates an HTTP response code. This status is suitable
 * for throwing an exception where the appropriate HTTP response for that failure
 * is specified.
 */
public class ServerStatus extends Status {
    private int httpCode;

    public ServerStatus(int severity, int httpCode, String message, Throwable exception) {
        super(severity, Activator.PI_SERVER_SERVLETS, message, exception);
        this.httpCode = httpCode;
    }

    public ServerStatus(IStatus status, int httpCode) {
        super(status.getSeverity(), status.getPlugin(), status.getCode(), status.getMessage(), status.getException());
        this.httpCode=httpCode;
    }

    public int getHttpCode() {
        return httpCode;
    }
}
