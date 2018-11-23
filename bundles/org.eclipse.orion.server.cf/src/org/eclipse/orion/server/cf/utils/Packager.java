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
package org.eclipse.orion.server.cf.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.ignore.IgnoreNode;
import org.eclipse.orion.server.core.IOUtilities;

/**
 * Utility class responsible for compressing application
 * contents according to the .cfignore rules.
 */
public class Packager {
	protected static String CF_IGNORE_FILE = ".cfignore"; //$NON-NLS-1$

	/**
	 * Reusing jgit ignore utilities.
	 * TODO: Consider a trie structure instead
	 *  of the hash map in order to reduce memory usage.
	 */
	protected HashMap<URI, IgnoreNode> cfIgnore;
	protected IFileStore base;

	public Packager(IFileStore base) {
		cfIgnore = new HashMap<URI, IgnoreNode>();
		this.base = base;
	}

	protected boolean isIgnored(IFileStore source, IPath path, boolean isDirectory) throws CoreException {

		/* look for inherited rules up to the base node */
		if (!base.isParentOf(source) && !base.equals(source))
			return false;

		IgnoreNode node = cfIgnore.get(source.toURI());

		if (node == null)
			return isIgnored(source.getParent(), path, isDirectory);

		IFileStore pathStore = base.getFileStore(path);
		URI relativeURI = URIUtil.makeRelative(pathStore.toURI(), source.toURI());

		switch (node.isIgnored(relativeURI.toString(), isDirectory)) {
			case IGNORED :
				return true;
			case NOT_IGNORED :
				return false;
			case CHECK_PARENT :
				/* fall over */
				break;
		}

		return isIgnored(source.getParent(), path, isDirectory);
	}

	protected void writeZip(IFileStore source, IPath path, ZipOutputStream zos) throws CoreException, IOException {

		/* load .cfignore if present */
		IFileStore cfIgnoreFile = source.getChild(CF_IGNORE_FILE);
		if (cfIgnoreFile.fetchInfo().exists()) {

			IgnoreNode node = new IgnoreNode();
			InputStream is = null;

			try {

				is = cfIgnoreFile.openInputStream(EFS.NONE, null);
				node.parse(is);

				cfIgnore.put(source.toURI(), node);

			} finally {
				if (is != null)
					is.close();
			}
		}

		IFileInfo info = source.fetchInfo(EFS.NONE, null);

		if (info.isDirectory()) {

			if (!isIgnored(source, path, true))
				for (IFileStore child : source.childStores(EFS.NONE, null))
					writeZip(child, path.append(child.getName()), zos);

		} else {

			if (!isIgnored(source, path, false)) {
				ZipEntry entry = new ZipEntry(path.toString());
				zos.putNextEntry(entry);
				IOUtilities.pipe(source.openInputStream(EFS.NONE, null), zos, true, false);
			}
		}
	}

	/**
	 * Compresses the given source to .zip format according to .cfignore rules.
	 * @param source Source store which has to be compressed.
	 * @param zos Zip output stream used as the compress destination.
	 * @throws IOException
	 * @throws CoreException
	 */
	public void writeZip(IFileStore source, ZipOutputStream zos) throws CoreException, IOException {
		writeZip(source, Path.EMPTY, zos);
	}
}