/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.manifest.v2.utils;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.IPath;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;

/**
 * Manifest inheritance utilities.
 */
public class InheritanceUtils {

	private static boolean hasApplication(ManifestParseTree applications, String applicationName) throws InvalidAccessException {
		for (ManifestParseTree application : applications.getChildren())
			if (application.get(ManifestConstants.NAME).getValue().equals(applicationName))
				return true;

		return false;
	}

	private static ManifestParseTree getApplication(ManifestParseTree applications, String applicationName) throws InvalidAccessException {
		for (ManifestParseTree application : applications.getChildren())
			if (application.get(ManifestConstants.NAME).getValue().equals(applicationName))
				return application;

		return null;
	}

	/**
	 * @param sandbox The file store used to limit manifest inheritance, i.e. each parent manifest has to be a
	 *  transitive child of the sandbox.
	 * @param current Manifest file store used to fetch the manifest contents.
	 * @param parent  Parent manifest file store path.
	 * @return whether the parent manifest is within the given sandbox or not.
	 */
	public static boolean isWithinSandbox(IFileStore sandbox, IFileStore current, IPath parent) {
		return sandbox.isParentOf(current.getParent().getFileStore(parent));
	}

	/**
	 * @param parent Parent manifest file store used to inherit from.
	 * @param child Manifest file store used to inherit from parent.
	 * @throws InvalidAccessException
	 */
	public static void inherit(ManifestParseTree parent, ManifestParseTree child) throws InvalidAccessException {

		/* inherit environment variables */
		if (parent.has(ManifestConstants.ENV)) {

			if (!child.has(ManifestConstants.ENV)) {
				ManifestParseTree env = new ManifestParseTree();
				env.setLabel(ManifestConstants.ENV);
				child.getChildren().add(0, env);
			}

			ManifestParseTree childEnv = child.get(ManifestConstants.ENV);
			for (ManifestParseTree envVar : parent.get(ManifestConstants.ENV).getChildren()) {
				String var = envVar.getLabel();
				if (!childEnv.has(var))
					childEnv.getChildren().add(envVar);
			}
		}

		/* inherit parent global properties */
		for (ManifestParseTree node : parent.getChildren()) {
			if (!ManifestUtils.isReserved(node))
				child.getChildren().add(node);
		}

		/* inherit parent applications */
		if (parent.has(ManifestConstants.APPLICATIONS)) {

			if (!child.has(ManifestConstants.APPLICATIONS)) {
				ManifestParseTree applications = new ManifestParseTree();
				applications.setLabel(ManifestConstants.APPLICATIONS);
				child.getChildren().add(applications);
			}

			int inherited = 0; /* preserve parent application order */
			ManifestParseTree childApplications = child.get(ManifestConstants.APPLICATIONS);
			for (ManifestParseTree parentApplication : parent.get(ManifestConstants.APPLICATIONS).getChildren()) {
				String app = parentApplication.get(ManifestConstants.NAME).getValue();
				if (!hasApplication(childApplications, app)) {

					childApplications.getChildren().add(inherited, parentApplication);
					++inherited;

				} else {

					/* inherit application properties */
					ManifestParseTree application = getApplication(childApplications, app);
					for (ManifestParseTree property : parentApplication.getChildren()) {
						if (!application.has(property.getLabel()))
							application.getChildren().add(property);
					}

					/* exception: check service inheritance */
					if (parentApplication.has(ManifestConstants.SERVICES)) {

						if (!application.has(ManifestConstants.SERVICES)) {
							ManifestParseTree services = new ManifestParseTree();
							services.setLabel(ManifestConstants.SERVICES);
							application.getChildren().add(services);
						}

						ManifestParseTree applicationServices = application.get(ManifestConstants.SERVICES);
						for (ManifestParseTree service : parentApplication.get(ManifestConstants.SERVICES).getChildren())
							if (!applicationServices.has(service.getLabel()))
								applicationServices.getChildren().add(service);
					}
				}
			}
		}
	}
}
