/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search;

/**
 * @author Aidan Redpath
 */
public class SearchException extends Exception {

	private static final long serialVersionUID = 4387363119495722008L;

	public SearchException(String message) {
		super(message);
	}

	public SearchException(Throwable cause) {
		super(cause.getMessage(), cause);
	}
}