/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.hosting;

/**
 * Superclass for exceptions that may occur when dealing with hosted sites.
 */
public class SiteHostingException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public SiteHostingException(String msg) {
		super(msg);
	}

	public SiteHostingException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
