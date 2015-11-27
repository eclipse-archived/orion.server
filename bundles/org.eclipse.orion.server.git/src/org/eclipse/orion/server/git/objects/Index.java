/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
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
import java.net.URISyntaxException;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.BaseToIndexConverter;

@ResourceDescription(type = Index.TYPE)
public class Index extends GitObject {

	public static final String RESOURCE = "index"; //$NON-NLS-1$
	public static final String TYPE = "Index"; //$NON-NLS-1$

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
		try {
			ObjectId blobId = entry.getObjectId();
			return db.open(blobId, Constants.OBJ_BLOB).openStream();
		} catch (MissingObjectException e) {
			return null;
		}
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return BaseToIndexConverter.getIndexLocation(cloneLocation, BaseToIndexConverter.CLONE);
	}
}
