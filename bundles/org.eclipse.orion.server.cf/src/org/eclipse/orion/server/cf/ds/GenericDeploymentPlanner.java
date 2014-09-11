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
package org.eclipse.orion.server.cf.ds;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.orion.server.cf.ds.objects.Plan;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GenericDeploymentPlanner implements IDeploymentPlanner {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$
	private static String TYPE = "generic"; //$NON-NLS-1$

	@Override
	public String getId() {
		return "org.eclipse.orion.server.cf.generic"; //$NON-NLS-1$
	}

	@Override
	public String getWidgetId() {
		return "org.eclipse.orion.client.cf.wizard.generic"; //$NON-NLS-1$
	}

	private String getApplicationName(IFileStore contentLocation) {
		return contentLocation.fetchInfo().getName();
	}

	@Override
	public Plan getDeploymentPlan(IFileStore contentLocation, ManifestParseTree manifest) {

		if (manifest != null)
			return new Plan(getId(), getWidgetId(), TYPE, manifest);

		try {

			String applicationName = getApplicationName(contentLocation);
			manifest = ManifestUtils.createBoilerplate(applicationName);

			/* set up generic defaults */
			ManifestParseTree application = manifest.get(ManifestConstants.APPLICATIONS).get(0);
			application.put(ManifestConstants.HOST, ManifestUtils.slugify(applicationName));
			application.put(ManifestConstants.MEMORY, ManifestUtils.DEFAULT_MEMORY);
			application.put(ManifestConstants.INSTANCES, ManifestUtils.DEFAULT_INSTANCES);
			application.put(ManifestConstants.PATH, ManifestUtils.DEFAULT_PATH);
			return new Plan(getId(), getWidgetId(), TYPE, manifest);

		} catch (Exception ex) {
			/* Nobody expected the Spanish inquisition */
			String msg = NLS.bind("Failed to handle generic deployment plan for {0}", contentLocation.toString()); //$NON-NLS-1$
			logger.error(msg, ex);
			return null;
		}
	}
}
