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

import java.io.InputStream;
import java.io.InputStreamReader;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.orion.server.cf.ds.IDeploymentPlanner;
import org.eclipse.orion.server.cf.ds.objects.Plan;
import org.eclipse.orion.server.cf.ds.objects.Procfile;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeJSDeploymentPlanner implements IDeploymentPlanner {
	protected final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$
	public static String TYPE = "node.js"; //$NON-NLS-1$

	@Override
	public String getId() {
		return getClass().getCanonicalName();
	}

	@Override
	public String getWizardId() {
		return "org.eclipse.orion.client.cf.wizard.nodejs"; //$NON-NLS-1$
	}

	protected String getApplicationName(IFileStore contentLocation) {
		String folderName = contentLocation.fetchInfo().getName();
		String[] folderNameParts = folderName.split(" --- ", 2);
		if (folderNameParts.length > 1)
			return folderNameParts[1];
		return folderNameParts[0];
	}

	protected String getApplicationHost(IFileStore contentLocation) {
		String folderName = contentLocation.fetchInfo().getName();
		return ManifestUtils.slugify(folderName);
	}

	protected void set(ManifestParseTree application, String property, String defaultValue) {
		if (application.has(property))
			return;
		else
			application.put(property, defaultValue);
	}

	/**
	 * Looks for a Procfile and parses the web command.
	 * @return <code>null</code> iff there is no Procfile present or it does not contain a web command.
	 */
	protected String getProcfileCommand(IFileStore contentLocation) {
		IFileStore procfileStore = contentLocation.getChild(ManifestConstants.PROCFILE);
		if (!procfileStore.fetchInfo().exists())
			return null;

		InputStream is = null;
		try {

			is = procfileStore.openInputStream(EFS.NONE, null);
			Procfile procfile = Procfile.load(is);
			return procfile.get(ManifestConstants.WEB);

		} catch (Exception ex) {
			/* can't parse the file, fail */
			return null;

		} finally {
			IOUtilities.safeClose(is);
		}
	}

	/**
	 * Looks for the package.json and parses the start command.
	 * @return <code>null</code> iff the package.json does not contain an explicit start command.
	 */
	protected String getPackageCommand(IFileStore contentLocation) {
		IFileStore packageStore = contentLocation.getChild(NodeJSConstants.PACKAGE_JSON);
		if (!packageStore.fetchInfo().exists())
			return null;

		InputStream is = null;
		try {

			is = packageStore.openInputStream(EFS.NONE, null);
			JSONObject packageJSON = new JSONObject(new JSONTokener(new InputStreamReader(is)));
			if (packageJSON.has(NodeJSConstants.SCRIPTS)) {
				JSONObject scripts = packageJSON.getJSONObject(NodeJSConstants.SCRIPTS);
				if (scripts.has(NodeJSConstants.START))
					return scripts.getString(NodeJSConstants.START);
			}

		} catch (Exception ex) {
			/* can't parse the file, fail */
			return null;

		} finally {
			IOUtilities.safeClose(is);
		}

		return null;
	}

	/**
	 * Looks for the app.js or server.js files and creates a start command.
	 * @return <code>null</code> iff both app.js and server.js are absent.
	 */
	protected String getConventionCommand(IFileStore contentLocation) {
		IFileStore serverJS = contentLocation.getChild(NodeJSConstants.SERVER_JS);
		if (serverJS.fetchInfo().exists())
			return NodeJSConstants.NODE_SERVER_JS;

		IFileStore appJS = contentLocation.getChild(NodeJSConstants.APP_JS);
		if (appJS.fetchInfo().exists())
			return NodeJSConstants.NODE_APP_JS;

		return null;
	}

	@Override
	public Plan getDeploymentPlan(IFileStore contentLocation, ManifestParseTree manifest) {

		/* a present package.json file determines a node.js application */
		IFileStore packageStore = contentLocation.getChild(NodeJSConstants.PACKAGE_JSON);
		if (!packageStore.fetchInfo().exists())
			return null;

		/* do not support multi-application manifests */
		if (manifest != null && ManifestUtils.hasMultipleApplications(manifest))
			return null;

		try {

			if (manifest == null)
				manifest = ManifestUtils.createBoilerplate(getApplicationName(contentLocation));

			ManifestParseTree application = manifest.get(ManifestConstants.APPLICATIONS).get(0);
			String defaultName = getApplicationName(contentLocation);

			set(application, ManifestConstants.NAME, defaultName);
			set(application, ManifestConstants.HOST, getApplicationHost(contentLocation));
			set(application, ManifestConstants.MEMORY, ManifestUtils.DEFAULT_MEMORY);
			set(application, ManifestConstants.INSTANCES, ManifestUtils.DEFAULT_INSTANCES);
			set(application, ManifestConstants.PATH, ManifestUtils.DEFAULT_PATH);

			/* node.js application require a start command */
			if (application.has(ManifestConstants.COMMAND))
				return new Plan(getId(), getWizardId(), TYPE, manifest);

			/* look up Procfile */
			String command = getProcfileCommand(contentLocation);
			if (command != null) {
				/* Do not set the command, buildpack will handle it */
				// application.put(ManifestConstants.COMMAND, command);
				return new Plan(getId(), getWizardId(), TYPE, manifest);
			}

			/* look up package.json */
			command = getPackageCommand(contentLocation);
			if (command != null) {
				/* Do not set the command, buildpack will handle it */
				// application.put(ManifestConstants.COMMAND, command);
				return new Plan(getId(), getWizardId(), TYPE, manifest);
			}

			command = getConventionCommand(contentLocation);
			if (command != null) {
				application.put(ManifestConstants.COMMAND, command);
				return new Plan(getId(), getWizardId(), TYPE, manifest);
			}

			/* could not deduce command, mark as required */
			Plan plan = new Plan(getId(), getWizardId(), TYPE, manifest);
			plan.addRequired(ManifestConstants.COMMAND);
			return plan;

		} catch (Exception ex) {
			String msg = NLS.bind("Failed to handle generic deployment plan for {0}", contentLocation.toString()); //$NON-NLS-1$
			logger.error(msg, ex);
			return null;
		}
	}
}
