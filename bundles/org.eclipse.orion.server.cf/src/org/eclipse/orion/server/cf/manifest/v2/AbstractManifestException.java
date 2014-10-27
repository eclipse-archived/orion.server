/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.manifest.v2;

import org.json.JSONObject;

public abstract class AbstractManifestException extends Exception {
	private static final long serialVersionUID = 1L;

	/* Error representation properties */
	public static final String ERROR_LINE = "Line"; //$NON-NLS-1$
	public static final String ERROR_MESSAGE = "Message"; //$NON-NLS-1$

	public static final String ERROR_SEVERITY = "Severity"; //$NON-NLS-1$
	public static final String WARNING = "Warning"; //$NON-NLS-1$

	/**
	 * Returns a detailed JSON representation of the exception.
	 * @return Either a JSON representation or <code>null</code> in case of a JSON exception.
	 */
	public abstract JSONObject getDetails();
}
