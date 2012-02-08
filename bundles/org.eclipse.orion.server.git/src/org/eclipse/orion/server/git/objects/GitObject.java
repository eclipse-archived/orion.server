/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.objects;

import java.net.URI;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

public abstract class GitObject {

	protected URI cloneLocation;
	protected Repository db;
	private StoredConfig cfg;

	GitObject(URI cloneLocation, Repository db) {
		this.cloneLocation = cloneLocation;
		this.db = db;
	}

	StoredConfig getConfig() {
		if (this.cfg == null) {
			this.cfg = db.getConfig();
		}
		return this.cfg;
	}
}
