/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core;

import java.net.URI;
import javax.servlet.http.HttpServletRequest;
import org.json.JSONObject;

/**
 * A resource decorator adds additional attributes to an HTTP resource representation
 * produced by another service. This allows a resource representation produced
 * by one service to be decorated with links or data from other services it knows
 * nothing about.
 */
public interface IWebResourceDecorator {
	/**
	 * Adds any additional attributes for the given resource to the provided map.
	 * @param resource The location of the resource to add attributes for
	 * @param representation The current representation of the resource
	 * @param req The current request
	 */
	public void addAtributesFor(HttpServletRequest req, URI resource, JSONObject representation);
}
