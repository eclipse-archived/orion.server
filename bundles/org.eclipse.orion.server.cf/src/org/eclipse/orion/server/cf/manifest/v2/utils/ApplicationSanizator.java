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

		try {
			ManifestParseTree applications = node.get("applications"); //$NON-NLS-1$
			for (ManifestParseTree application : applications.getChildren()) {

				String applicationName = application.get("name").getValue(); //$NON-NLS-1$

				ManifestParseTree memory = application.getOpt("memory"); //$NON-NLS-1$
				checkMemory(applicationName, memory);

				ManifestParseTree instances = application.getOpt("instances"); //$NON-NLS-1$
				checkInstances(applicationName, instances);

				ManifestParseTree timeout = application.getOpt("timeout"); //$NON-NLS-1$
				checkTimeout(applicationName, timeout);

				ManifestParseTree noRoute = application.getOpt("no-route"); //$NON-NLS-1$
				checkNoRoute(applicationName, noRoute);
			}
		} catch (InvalidAccessException ex) {
			/* invalid manifest structure, fail */
			throw new AnalyzerException(ex.getMessage());
		}
	}

	private void checkMemory(String applicationName, ManifestParseTree memory) throws AnalyzerException, InvalidAccessException {
		if (memory == null)
			return;

		String memoryValue = memory.getValue();
		Matcher matcher = memoryPattern.matcher(memoryValue);
		if (!matcher.matches())
			throw new AnalyzerException(NLS.bind("Invalid memory limit for application \"{0}\". Supported measurement units are M/MB, G/GB.", applicationName));
	}

	private void checkInstances(String applicationName, ManifestParseTree instances) throws AnalyzerException, InvalidAccessException {
		if (instances == null)
			return;

		String instancesValue = instances.getValue();
		Matcher matcher = nonNegativePattern.matcher(instancesValue);
		if (!matcher.matches())
			throw new AnalyzerException(NLS.bind("Invalid \"instances\" value for application \"{0}\". Expected a non-negative integer value.", applicationName));
	}

	private void checkTimeout(String applicationName, ManifestParseTree timeout) throws AnalyzerException, InvalidAccessException {
		if (timeout == null)
			return;

		String timeoutValue = timeout.getValue();
		Matcher matcher = nonNegativePattern.matcher(timeoutValue);
		if (!matcher.matches())
			throw new AnalyzerException(NLS.bind("Invalid \"timeout\" value for application \"{0}\". Expected a non-negative integer value.", applicationName));
	}

	private void checkNoRoute(String applicationName, ManifestParseTree noRoute) throws AnalyzerException, InvalidAccessException {
		if (noRoute == null)
			return;

		String noRouteValue = noRoute.getValue();
		if (!"true".equals(noRouteValue)) //$NON-NLS-1$
			throw new AnalyzerException(NLS.bind("Invalid \"no-route\" value for application \"{0}\". Expected a string literal \"true\".", applicationName));
	}
}
