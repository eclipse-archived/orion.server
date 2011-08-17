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
package org.eclipse.orion.server.git.objects;

import java.io.IOException;
import java.net.URI;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.*;

public class Index extends GitObject {

	public static final String RESOURCE = "index"; //$NON-NLS-1$

	private String pattern;

	public Index(URI cloneLocation, Repository db, String pattern) {
		super(cloneLocation, db);
		this.pattern = pattern;
	}

	public ObjectStream toObjectStream() throws IOException {
		DirCache cache = db.readDirCache();
		DirCacheEntry entry = cache.getEntry(pattern);
		if (entry == null) {
			return null;
		}
		ObjectId blobId = entry.getObjectId();
		return db.open(blobId, Constants.OBJ_BLOB).openStream();
	}
}
