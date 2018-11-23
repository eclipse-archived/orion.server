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
package org.eclipse.orion.server.cf.nodejs;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.utils.Packager;
import org.eclipse.orion.server.core.IOUtilities;
import org.json.JSONObject;

/**
 * A cf-launcher packager which ignores the default workspace
 * package.json file and includes a given JSON object instead.
 */
public class CFLauncherPackager extends Packager {

	protected JSONObject packageJSON;
	protected ManifestParseTree manifest;

	public CFLauncherPackager(IFileStore base, JSONObject packageJSON) {
		super(base);
		this.packageJSON = packageJSON;
	}

	protected boolean isPackageJSON(IFileStore source, IPath path) {
		return (NodeJSConstants.PACKAGE_JSON.equals(path.toString()) && //
		NodeJSConstants.PACKAGE_JSON.equals(source.fetchInfo().getName()));
	}

	@Override
	protected boolean isIgnored(IFileStore source, IPath path, boolean isDirectory) throws CoreException {

		/* ignore top-level package.json */
		return isPackageJSON(source, path) ? true : super.isIgnored(source, path, isDirectory);
	}

	@Override
	public void writeZip(IFileStore source, ZipOutputStream zos) throws CoreException, IOException {

		/* prepare deployment package */
		writeZip(source, Path.EMPTY, zos);

		/* deliver modified package.json with manifest.yml */
		InputStream packageStream = new ByteArrayInputStream(packageJSON.toString().getBytes("UTF-8"));
		zos.putNextEntry(new ZipEntry(Path.EMPTY.append(NodeJSConstants.PACKAGE_JSON).toString()));
		IOUtilities.pipe(packageStream, zos, true, false);
	}
}
