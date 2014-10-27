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

import java.util.Iterator;
import org.eclipse.orion.server.cf.manifest.v2.*;

public class ApplicationReconstructor implements Analyzer {

	@Override
	public void apply(ManifestParseTree node) throws AnalyzerException {

		if (!node.has(ManifestConstants.APPLICATIONS))
			/* nothing to do */
			return;

		try {

			ManifestParseTree applications = node.get(ManifestConstants.APPLICATIONS);
			for (ManifestParseTree application : applications.getChildren()) {

				removeEmptyProperties(application);
				ensureStringProperty(application, application.getOpt(ManifestConstants.NAME));
				ensureStringProperty(application, application.getOpt(ManifestConstants.BUILDPACK));
				ensureStringProperty(application, application.getOpt(ManifestConstants.COMMAND));
				ensureStringProperty(application, application.getOpt(ManifestConstants.DOMAIN));
				ensureStringProperty(application, application.getOpt(ManifestConstants.HOST));
				ensureStringProperty(application, application.getOpt(ManifestConstants.PATH));

				ensureMemoryProperty(application, application.getOpt(ManifestConstants.MEMORY));

				ensureNonNegativeProperty(application, application.getOpt(ManifestConstants.INSTANCES));
				ensureNonNegativeProperty(application, application.getOpt(ManifestConstants.TIMEOUT));

				ensureNoRouteProperty(application, application.getOpt(ManifestConstants.NOROUTE));

				ensureServicesProperty(application, application.getOpt(ManifestConstants.SERVICES));
			}

		} catch (InvalidAccessException ex) {
			/* invalid manifest structure, fail */
			throw new AnalyzerException(ex.getMessage());
		}
	}

	protected void removeEmptyProperties(ManifestParseTree application) throws AnalyzerException {
		for (Iterator<ManifestParseTree> it = application.getChildren().iterator(); it.hasNext();) {
			ManifestParseTree node = it.next();
			if (node.getChildren().isEmpty())
				it.remove();
		}
	}

	protected void ensureStringProperty(ManifestParseTree parent, ManifestParseTree node) {
		if (node == null)
			return;

		if (!node.isStringProperty())
			parent.getChildren().remove(node);
	}

	protected void ensureMemoryProperty(ManifestParseTree parent, ManifestParseTree node) {
		if (node == null)
			return;

		if (!node.isValidMemoryProperty())
			parent.getChildren().remove(node);
	}

	protected void ensureNonNegativeProperty(ManifestParseTree parent, ManifestParseTree node) {
		if (node == null)
			return;

		if (!node.isValidNonNegativeProperty())
			parent.getChildren().remove(node);
	}

	protected void ensureNoRouteProperty(ManifestParseTree parent, ManifestParseTree node) {
		if (node == null)
			return;

		if (!node.isStringProperty())
			parent.getChildren().remove(node);

		try {

			String noRouteValue = node.getValue();
			if (!"true".equals(noRouteValue)) //$NON-NLS-1$
				parent.getChildren().remove(node);

		} catch (InvalidAccessException e) {
			parent.getChildren().remove(node);
		}
	}

	protected void ensureServicesProperty(ManifestParseTree parent, ManifestParseTree node) {
		if (node == null)
			return;

		if (node.isStringProperty())
			parent.getChildren().remove(node);
	}
}
