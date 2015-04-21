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

import org.eclipse.orion.server.cf.manifest.v2.Analyzer;
import org.eclipse.orion.server.cf.manifest.v2.AnalyzerException;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.osgi.util.NLS;

/**
 * Ensures the following application properties:
 * 
 * a) each application has a name
 * b) memory limits are well formatted (supported M/MB, G/GB units) if present
 * c) instances are non-negative integers if present
 * d) timeouts are non-negative integers if present
 * e) no-routes are strings literals "true" if present
 */
public class ApplicationSanizator implements Analyzer {

	@Override
	public void apply(ManifestParseTree node) throws AnalyzerException {

		if (!node.has(ManifestConstants.APPLICATIONS))
			/* nothing to do */
			return;

		try {
			ManifestParseTree applications = node.get(ManifestConstants.APPLICATIONS);
			for (ManifestParseTree application : applications.getChildren()) {

				String applicationName = application.get(ManifestConstants.NAME).getValue();

				checkEmptyProperties(applicationName, application);

				ManifestParseTree buildpack = application.getOpt(ManifestConstants.BUILDPACK);
				checkBuildpack(applicationName, buildpack);

				ManifestParseTree command = application.getOpt(ManifestConstants.COMMAND);
				checkCommand(applicationName, command);

				ManifestParseTree domain = application.getOpt(ManifestConstants.DOMAIN);
				checkDomain(applicationName, domain);

				ManifestParseTree host = application.getOpt(ManifestConstants.HOST);
				checkHost(applicationName, host);

				ManifestParseTree path = application.getOpt(ManifestConstants.PATH);
				checkPath(applicationName, path);

				ManifestParseTree memory = application.getOpt(ManifestConstants.MEMORY);
				checkMemory(applicationName, memory);

				ManifestParseTree instances = application.getOpt(ManifestConstants.INSTANCES);
				checkInstances(applicationName, instances);

				ManifestParseTree timeout = application.getOpt(ManifestConstants.TIMEOUT);
				checkTimeout(applicationName, timeout);

				ManifestParseTree noRoute = application.getOpt(ManifestConstants.NOROUTE);
				checkNoRoute(applicationName, noRoute);

				ManifestParseTree services = application.getOpt(ManifestConstants.SERVICES);
				checkServices(applicationName, services);
			}
		} catch (InvalidAccessException ex) {
			/* invalid manifest structure, fail */
			throw new AnalyzerException(ex.getMessage());
		}
	}

	protected void checkEmptyProperties(String applicationName, ManifestParseTree application) throws AnalyzerException {
		for (ManifestParseTree property : application.getChildren())
			if (property.getChildren().isEmpty())
				throw new AnalyzerException(NLS.bind("Empty property \"{0}\" in application \"{1}\".", property.getLabel(), applicationName), property.getLineNumber());
	}

	protected void checkServices(String applicationName, ManifestParseTree services) throws AnalyzerException {
		if (services == null)
			return;

		if (services.isStringProperty())
			throw new AnalyzerException(NLS.bind("Invalid services declaration for application \"{0}\". Expected a list of service names.", applicationName), services.getLineNumber());
	}

	protected void checkBuildpack(String applicationName, ManifestParseTree buildpack) throws AnalyzerException {
		if (buildpack == null)
			return;

		if (!buildpack.isStringProperty())
			throw new AnalyzerException(NLS.bind("Invalid \"buildpack\" value for application \"{0}\". Expected a string literal.", applicationName), buildpack.getLineNumber());
	}

	protected void checkCommand(String applicationName, ManifestParseTree command) throws AnalyzerException {
		if (command == null)
			return;

		if (!command.isStringProperty())
			throw new AnalyzerException(NLS.bind("Invalid \"command\" value for application \"{0}\". Expected a string literal.", applicationName), command.getLineNumber());
	}

	protected void checkDomain(String applicationName, ManifestParseTree domain) throws AnalyzerException {
		if (domain == null)
			return;

		if (!domain.isStringProperty())
			throw new AnalyzerException(NLS.bind("Invalid \"domain\" value for application \"{0}\". Expected a string literal.", applicationName), domain.getLineNumber());
	}

	protected void checkHost(String applicationName, ManifestParseTree host) throws AnalyzerException {
		if (host == null)
			return;

		if (!host.isStringProperty())
			throw new AnalyzerException(NLS.bind("Invalid \"host\" value for application \"{0}\". Expected a string literal.", applicationName), host.getLineNumber());
	}

	protected void checkPath(String applicationName, ManifestParseTree path) throws AnalyzerException {
		if (path == null)
			return;

		if (!path.isStringProperty())
			throw new AnalyzerException(NLS.bind("Invalid \"path\" value for application \"{0}\". Expected a path string.", applicationName), path.getLineNumber());
	}

	protected void checkMemory(String applicationName, ManifestParseTree memory) throws AnalyzerException, InvalidAccessException {
		if (memory == null)
			return;

		if (!memory.isStringProperty())
			throw new AnalyzerException(NLS.bind("Invalid \"memory\" value for application \"{0}\". Expected a memory limit.", applicationName), memory.getLineNumber());

		if (!memory.isValidMemoryProperty())
			throw new AnalyzerException(NLS.bind("Invalid memory limit for application \"{0}\". Supported measurement units are M/MB, G/GB.", applicationName), memory.getLineNumber());
	}

	protected void checkInstances(String applicationName, ManifestParseTree instances) throws AnalyzerException, InvalidAccessException {
		if (instances == null)
			return;

		if (!instances.isStringProperty())
			throw new AnalyzerException(NLS.bind("Invalid \"instances\" value for application \"{0}\". Expected a non-negative integer value.", applicationName), instances.getLineNumber());

		if (!instances.isValidNonNegativeProperty())
			throw new AnalyzerException(NLS.bind("Invalid \"instances\" value for application \"{0}\". Expected a non-negative integer value.", applicationName), instances.getLineNumber());
	}

	protected void checkTimeout(String applicationName, ManifestParseTree timeout) throws AnalyzerException, InvalidAccessException {
		if (timeout == null)
			return;

		if (!timeout.isStringProperty())
			throw new AnalyzerException(NLS.bind("Invalid \"timeout\" value for application \"{0}\". Expected a non-negative integer value.", applicationName), timeout.getLineNumber());

		if (!timeout.isValidNonNegativeProperty())
			throw new AnalyzerException(NLS.bind("Invalid \"timeout\" value for application \"{0}\". Expected a non-negative integer value.", applicationName), timeout.getLineNumber());
	}

	protected void checkNoRoute(String applicationName, ManifestParseTree noRoute) throws AnalyzerException, InvalidAccessException {
		if (noRoute == null)
			return;

		if (!noRoute.isStringProperty())
			throw new AnalyzerException(NLS.bind("Invalid \"no-route\" value for application \"{0}\". Expected a string literal \"true\".", applicationName), noRoute.getLineNumber());

		String noRouteValue = noRoute.getValue();
		if (!"true".equals(noRouteValue)) //$NON-NLS-1$
			throw new AnalyzerException(NLS.bind("Invalid \"no-route\" value for application \"{0}\". Expected a string literal \"true\".", applicationName), noRoute.getLineNumber());
	}
}
