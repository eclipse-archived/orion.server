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

import java.util.*;

/**
 * A single application Cloud Foundry deployment representation.
 */
public final class DeploymentDescription {

	private List<String> services;
	private Map<String, String> properties;

	private String applicationName;
	private String applicationType;

	public DeploymentDescription(String applicationName, String applicationType) {
		this.applicationName = applicationName;
		this.applicationType = applicationType;

		services = new ArrayList<String>();
		properties = new HashMap<String, String>();
	}

	public String getApplicationName() {
		return applicationName;
	}

	public String getApplicationType() {
		return applicationType;
	}

	public DeploymentDescription addService(String service) {
		services.add(service);
		return this;
	}

	public DeploymentDescription add(String key, String value) {
		properties.put(key, value);
		return this;
	}

	@Override
	public String toString() {

		StringBuilder sb = new StringBuilder();
		String newLine = System.getProperty("line.separator");

		sb.append("---").append(newLine);
		sb.append("applications:").append(newLine);
		sb.append(" - name: ").append(applicationName);

		for (String key : properties.keySet()) {
			sb.append(newLine).append("   ").append(key);
			sb.append(": ").append(properties.get(key));
		}

		if (!services.isEmpty()) {
			sb.append(newLine).append("   ").append("services:");
			for (String service : services)
				sb.append(newLine).append("   ").append("- ").append(service);
		}

		sb.append(newLine);
		return sb.toString();
	}
}