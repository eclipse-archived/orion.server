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
package org.eclipse.orion.server.cf.service;

import java.io.*;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.osgi.util.NLS;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default IDeploymentService implementation.
 * @see IDeploymentService
 */
public class DeploymentService implements IDeploymentService {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf");

	/* 100KB lookup RAM memory limit */
	private static long MAX_FILE_LENGTH = 1024 * 100;

	/* default command */
	private static String DEFAULT_COMMAND = "node app.js";

	@Override
	public String getType(IFileStore application) {

		/* package.json in the top-level folder determines a node.js application */
		if (application.getChild(DeploymentConstants.PACKAGE_JSON).fetchInfo().exists())
			return DeploymentConstants.NODE_JS;

		return DeploymentConstants.UNKNOWN_TYPE;
	}

	private DeploymentDescription handleNodeJS(String applicationName, IFileStore application) throws CoreException, IOException, JSONException {
		DeploymentDescription description = new DeploymentDescription(applicationName, DeploymentConstants.NODE_JS);

		/* add static, default properties */
		description.add(CFProtocolConstants.V2_KEY_MEMORY, "512MB");
		description.add(CFProtocolConstants.V2_KEY_INSTANCES, "1");
		description.add(CFProtocolConstants.V2_KEY_PATH, ".");

		/* set up the command property:
		 * 1) Look in the Procfile for a command in form of "web: <command>" 
		 * 2) Look in the package.json scripts section under "start"
		 * 3) Look if "app.js" is present and assume the command: "node app.js" */

		IFileStore procfile = application.getChild(DeploymentConstants.PROCFILE);
		if (procfile.fetchInfo().exists() && procfile.fetchInfo().getLength() < MAX_FILE_LENGTH) {
			InputStream inputStream = procfile.openInputStream(EFS.NONE, null);
			BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
			try {

				String inputLine = null;
				while ((inputLine = br.readLine()) != null) {
					if (inputLine.startsWith(DeploymentConstants.PROCFILE_WEB)) {
						String command = inputLine.substring(DeploymentConstants.PROCFILE_WEB.length());
						description.add(CFProtocolConstants.V2_KEY_COMMAND, command.trim());
						return description;
					}
				}

			} finally {
				if (br != null)
					br.close();
			}
		}

		IFileStore packageJSON = application.getChild(DeploymentConstants.PACKAGE_JSON);
		if (packageJSON.fetchInfo().exists() && packageJSON.fetchInfo().getLength() < MAX_FILE_LENGTH) {

			InputStream inputStream = packageJSON.openInputStream(EFS.NONE, null);
			InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

			try {

				JSONObject packageJSONObject = new JSONObject(new JSONTokener(inputStreamReader));
				if (packageJSONObject.has(DeploymentConstants.SCRIPTS)) {
					JSONObject scripts = packageJSONObject.getJSONObject(DeploymentConstants.SCRIPTS);
					if (scripts.has(DeploymentConstants.START)) {
						String command = scripts.getString(DeploymentConstants.START);
						description.add(CFProtocolConstants.V2_KEY_COMMAND, command.trim());
						return description;
					}
				}

			} finally {
				if (inputStreamReader != null)
					inputStreamReader.close();
			}
		}

		IFileStore appJS = application.getChild(DeploymentConstants.APP_JS);
		if (appJS.fetchInfo().exists())
			description.add(CFProtocolConstants.V2_KEY_COMMAND, DEFAULT_COMMAND);

		return description;
	}

	@Override
	public DeploymentDescription getDeploymentDescription(String applicationName, IFileStore application) {

		try {

			/* case through known application types */
			String applicationType = getType(application);
			if (DeploymentConstants.NODE_JS.equals(applicationType))
				return handleNodeJS(applicationName, application);

			/* unsupported application type */
			return null;

		} catch (Exception ex) {
			logger.error(NLS.bind("Failed to determine application type for {0}", applicationName), ex);
			return null;
		}
	}
}