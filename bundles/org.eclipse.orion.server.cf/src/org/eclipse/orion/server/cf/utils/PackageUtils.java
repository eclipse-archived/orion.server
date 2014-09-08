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

import java.io.*;
import java.util.UUID;
import java.util.zip.ZipOutputStream;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.internal.filesystem.local.LocalFile;
import org.eclipse.core.runtime.CoreException;

/**
 * Packaging utilities facilitating the Cloud Foundry bits upload process.
 */
public class PackageUtils {

	/**
	 * Compresses the given source to .zip format.
	 * @param source Source store which has to be compressed recursively.
	 * @param zos Zip output stream used as the compress destination.
	 * @throws IOException
	 * @throws CoreException
	 */
	public static void writeZip(IFileStore source, ZipOutputStream zos) throws IOException, CoreException {
		Packager packager = new Packager(source);
		packager.writeZip(source, zos);
	}

	/**
	 * Packages the application contents.
	 * @param applicationStore Application source file store.
	 * @return Depending on the application contents, either a zipped .war file, or single .zip file with application contents.
	 * @throws IOException
	 * @throws CoreException
	 */
	public static File getApplicationPackage(IFileStore applicationStore) throws IOException, CoreException {
		if (applicationStore == null || !applicationStore.fetchInfo().exists())
			return null;

		File tmp = null;
		ZipOutputStream zos = null;

		try {

			/* zip application to a temporary file */
			String randomName = UUID.randomUUID().toString();
			tmp = File.createTempFile(randomName, ".zip"); //$NON-NLS-1$

			/* check whether the application store is a war file */
			if (applicationStore.getName().endsWith(".war")) { //$NON-NLS-1$
				applicationStore.copy(new LocalFile(tmp), EFS.OVERWRITE, null);
				return tmp;
			}

			/* check whether the application store contains a war file */
			for (IFileStore child : applicationStore.childStores(EFS.NONE, null)) {
				if (child.getName().endsWith(".war")) { //$NON-NLS-1$
					child.copy(new LocalFile(tmp), EFS.OVERWRITE, null);
					return tmp;
				}
			}

			/* war is not the answer, zip application contents */
			zos = new ZipOutputStream(new FileOutputStream(tmp));
			writeZip(applicationStore, zos);
			return tmp;

		} catch (Exception ex) {
			if (tmp != null)
				tmp.delete();

			return null;

		} finally {
			if (zos != null)
				zos.close();
		}
	}

	public static String getApplicationPackageType(IFileStore applicationStore) throws CoreException {

		if (applicationStore == null || !applicationStore.fetchInfo().exists())
			return "unknown"; //$NON-NLS-1$

		/* check whether the application store is a war file */
		if (applicationStore.getName().endsWith(".war")) { //$NON-NLS-1$
			return "war"; //$NON-NLS-1$
		}

		/* check whether the application store contains a war file */
		for (IFileStore child : applicationStore.childStores(EFS.NONE, null)) {
			if (child.getName().endsWith(".war")) { //$NON-NLS-1$
				return "war"; //$NON-NLS-1$
			}
		}

		return "zip"; //$NON-NLS-1$
	}
}
