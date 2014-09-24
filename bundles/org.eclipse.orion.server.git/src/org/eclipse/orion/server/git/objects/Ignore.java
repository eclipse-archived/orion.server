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
package org.eclipse.orion.server.git.objects;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;

@ResourceDescription(type = Ignore.TYPE)
public class Ignore extends GitObject {

	public static final String RESOURCE = "ignore"; //$NON-NLS-1$
	public static final String TYPE = "Ignore"; //$NON-NLS-1$

	private URI cloneLocation;

	public Ignore(URI cloneLocation, Repository db) {
		super(cloneLocation, db);
		this.cloneLocation = cloneLocation;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return cloneLocation;
	}
}
