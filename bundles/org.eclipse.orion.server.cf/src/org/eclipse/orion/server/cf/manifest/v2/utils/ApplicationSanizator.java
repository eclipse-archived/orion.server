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
package org.eclipse.orion.server.cf.manifest.v2.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.orion.server.cf.manifest.v2.*;
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
	Pattern memoryPattern = Pattern.compile("[1-9][0-9]*(M|MB|G|GB|m|mb|g|gb)"); //$NON-NLS-1$
	Pattern nonNegativePattern = Pattern.compile("[1-9][0-9]*"); //$NON-NLS-1$

	@Override
	public void apply(ManifestParseTree node) throws AnalyzerException {

		if (!node.has("applications")) //$NON-NLS-1$
			/* nothing to do */
			return;

		try {
			ManifestParseTree applications = node.get("applications"); //$NON-NLS-1$
			for (ManifestParseTree application : applications.getChildren()) {

				String applicationName = application.get("name").getValue(); //$NON-NLS-1$

				checkEmptyProperties(applicationName, application);

				ManifestParseTree buildpack = application.getOpt("buildpack"); //$NON-NLS-1$
				checkBuildpack(applicationName, buildpack);

				ManifestParseTree command = application.getOpt("command"); //$NON-NLS-1$
				checkCommand(applicationName, command);

				ManifestParseTree domain = application.getOpt("domain"); //$NON-NLS-1$
				checkDomain(applicationName, domain);

				ManifestParseTree host = application.getOpt("host"); //$NON-NLS-1$
				checkHost(applicationName, host);

				ManifestParseTree path = application.getOpt("path"); //$NON-NLS-1$
				checkPath(applicationName, path);

				ManifestParseTree memory = application.getOpt("memory"); //$NON-NLS-1$
				checkMemory(applicationName, memory);

				ManifestParseTree instances = application.getOpt("instances"); //$NON-NLS-1$
				checkInstances(applicationName, instances);

				ManifestParseTree timeout = application.getOpt("timeout"); //$NON-NLS-1$
				checkTimeout(applicationName, timeout);

				ManifestParseTree noRoute = application.getOpt("no-route"); //$NON-NLS-1$
				checkNoRoute(applicationName, noRoute);

				ManifestParseTree services = application.getOpt("services"); //$NON-NLS-1$
				checkServices(applicationName, services);
			}
		} catch (InvalidAccessException ex) {
			/* invalid manifest structure, fail */
			throw new AnalyzerException(ex.getMessage());
		}
	}

	private boolean isStringProperty(ManifestParseTree node) {
		if (node.getChildren().size() != 1)
			return false;

		if (node.isList())
			return false;

		ManifestParseTree valueNode = node.getChildren().get(0);
		if (valueNode.getChildren().size() != 0)
			return false;

		return true;
	}

	private void checkEmptyProperties(String applicationName, ManifestParseTree application) throws AnalyzerException {
		for (ManifestParseTree property : application.getChildren())
			if (property.getChildren().isEmpty())
				throw new AnalyzerException(NLS.bind("Empty property \"{0}\" in application \"{1}\".", property.getLabel(), applicationName));
	}

	private void checkServices(String applicationName, ManifestParseTree services) throws AnalyzerException {
		if (services == null)
			return;

		if (isStringProperty(services))
			throw new AnalyzerException(NLS.bind("Invalid services declaration for application \"{0}\". Expected a list of service names.", applicationName));
	}

	private void checkBuildpack(String applicationName, ManifestParseTree buildpack) throws AnalyzerException {
		if (buildpack == null)
			return;

		if (!isStringProperty(buildpack))
			throw new AnalyzerException(NLS.bind("Invalid \"buildpack\" value for application \"{0}\". Expected a string literal.", applicationName));
	}

	private void checkCommand(String applicationName, ManifestParseTree command) throws AnalyzerException {
		if (command == null)
			return;

		if (!isStringProperty(command))
			throw new AnalyzerException(NLS.bind("Invalid \"command\" value for application \"{0}\". Expected a string literal.", applicationName));
	}

	private void checkDomain(String applicationName, ManifestParseTree domain) throws AnalyzerException {
		if (domain == null)
			return;

		if (!isStringProperty(domain))
			throw new AnalyzerException(NLS.bind("Invalid \"domain\" value for application \"{0}\". Expected a string literal.", applicationName));
	}

	private void checkHost(String applicationName, ManifestParseTree host) throws AnalyzerException {
		if (host == null)
			return;

		if (!isStringProperty(host))
			throw new AnalyzerException(NLS.bind("Invalid \"host\" value for application \"{0}\". Expected a string literal.", applicationName));
	}

	private void checkPath(String applicationName, ManifestParseTree path) throws AnalyzerException {
		if (path == null)
			return;

		if (!isStringProperty(path))
			throw new AnalyzerException(NLS.bind("Invalid \"path\" value for application \"{0}\". Expected a path string.", applicationName));
	}

	private void checkMemory(String applicationName, ManifestParseTree memory) throws AnalyzerException, InvalidAccessException {
		if (memory == null)
			return;

		if (!isStringProperty(memory))
			throw new AnalyzerException(NLS.bind("Invalid \"memory\" value for application \"{0}\". Expected a memory limit.", applicationName));

		String memoryValue = memory.getValue();
		Matcher matcher = memoryPattern.matcher(memoryValue);
		if (!matcher.matches())
			throw new AnalyzerException(NLS.bind("Invalid memory limit for application \"{0}\". Supported measurement units are M/MB, G/GB.", applicationName));
	}

	private void checkInstances(String applicationName, ManifestParseTree instances) throws AnalyzerException, InvalidAccessException {
		if (instances == null)
			return;

		if (!isStringProperty(instances))
			throw new AnalyzerException(NLS.bind("Invalid \"instances\" value for application \"{0}\". Expected a non-negative integer value.", applicationName));

		String instancesValue = instances.getValue();
		Matcher matcher = nonNegativePattern.matcher(instancesValue);
		if (!matcher.matches())
			throw new AnalyzerException(NLS.bind("Invalid \"instances\" value for application \"{0}\". Expected a non-negative integer value.", applicationName));
	}

	private void checkTimeout(String applicationName, ManifestParseTree timeout) throws AnalyzerException, InvalidAccessException {
		if (timeout == null)
			return;

		if (!isStringProperty(timeout))
			throw new AnalyzerException(NLS.bind("Invalid \"timeout\" value for application \"{0}\". Expected a non-negative integer value.", applicationName));

		String timeoutValue = timeout.getValue();
		Matcher matcher = nonNegativePattern.matcher(timeoutValue);
		if (!matcher.matches())
			throw new AnalyzerException(NLS.bind("Invalid \"timeout\" value for application \"{0}\". Expected a non-negative integer value.", applicationName));
	}

	private void checkNoRoute(String applicationName, ManifestParseTree noRoute) throws AnalyzerException, InvalidAccessException {
		if (noRoute == null)
			return;

		if (!isStringProperty(noRoute))
			throw new AnalyzerException(NLS.bind("Invalid \"no-route\" value for application \"{0}\". Expected a string literal \"true\".", applicationName));

		String noRouteValue = noRoute.getValue();
		if (!"true".equals(noRouteValue)) //$NON-NLS-1$
			throw new AnalyzerException(NLS.bind("Invalid \"no-route\" value for application \"{0}\". Expected a string literal \"true\".", applicationName));
	}
}
