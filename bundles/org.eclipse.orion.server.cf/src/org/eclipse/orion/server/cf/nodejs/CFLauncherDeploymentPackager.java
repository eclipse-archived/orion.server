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
import java.util.UUID;
import java.util.zip.ZipOutputStream;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.cf.ds.IDeploymentPackager;
import org.eclipse.orion.server.core.IOUtilities;
import org.json.*;

/** 
 * A cf-launcher deployment packager which prepares a customized node.js
 * deployment package according to https://www.npmjs.org/package/cf-launcher#installation.
 */
public class CFLauncherDeploymentPackager implements IDeploymentPackager {

	protected static final String CF_LAUNCHER = "cf-launcher"; //$NON-NLS-1$
	protected static final String CF_LAUNCHER_VERSION = "0.0.x"; //$NON-NLS-1$

	@Override
	public String getId() {
		return getClass().getCanonicalName();
	}

	/**
	 * Sets up the package.json "dependencies" property. In case it's already
	 * present, tries to 'correct' the property to a JSON object without
	 * doing any harm to the package.json semantic. 
	 * @param packageJSON
	 * @throws JSONException
	 */
	protected void setupDependencies(JSONObject packageJSON) throws JSONException {

		if (!packageJSON.has(NodeJSConstants.DEPENDENCIES)) {
			packageJSON.put(NodeJSConstants.DEPENDENCIES, new JSONObject());
			return;
		}

		Object dependencies = packageJSON.get(NodeJSConstants.DEPENDENCIES);
		if (dependencies instanceof JSONArray) {
			JSONArray dependencyArray = (JSONArray) dependencies;
			if (dependencyArray.length() == 0) {
				packageJSON.remove(NodeJSConstants.DEPENDENCIES);
				packageJSON.put(NodeJSConstants.DEPENDENCIES, new JSONObject());
				return;
			}
		}

		if (dependencies instanceof String) {
			String dependencyString = (String) dependencies;
			if (dependencyString == null || dependencyString.isEmpty()) {
				packageJSON.remove(NodeJSConstants.DEPENDENCIES);
				packageJSON.put(NodeJSConstants.DEPENDENCIES, new JSONObject());
				return;
			}
		}
	}

	@Override
	public File getDeploymentPackage(IFileStore contentLocation) throws IOException, CoreException {

		/* require a package.json file present */
		IFileStore packageStore = contentLocation.getChild(NodeJSConstants.PACKAGE_JSON);
		if (!packageStore.fetchInfo().exists())
			return null;

		InputStream is = null;
		JSONObject packageJSON = null;

		try {

			is = packageStore.openInputStream(EFS.NONE, null);
			packageJSON = new JSONObject(new JSONTokener(new InputStreamReader(is)));

			if (!packageJSON.has(NodeJSConstants.DEPENDENCIES))
				packageJSON.put(NodeJSConstants.DEPENDENCIES, new JSONObject());

			setupDependencies(packageJSON);

			JSONObject dependencies = packageJSON.getJSONObject(NodeJSConstants.DEPENDENCIES);
			if (!dependencies.has(CF_LAUNCHER))
				dependencies.put(CF_LAUNCHER, CF_LAUNCHER_VERSION);

		} catch (JSONException ex) {
			/* TODO: Consider an error report here */
			return null;

		} finally {
			IOUtilities.safeClose(is);
		}

		File tmp = null;
		ZipOutputStream zos = null;

		try {

			/* zip application to a temporary file */
			String randomName = UUID.randomUUID().toString();
			tmp = File.createTempFile(randomName, ".zip"); //$NON-NLS-1$

			zos = new ZipOutputStream(new FileOutputStream(tmp));
			CFLauncherPackager packager = new CFLauncherPackager(contentLocation, packageJSON);
			packager.writeZip(contentLocation, zos);
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
}
