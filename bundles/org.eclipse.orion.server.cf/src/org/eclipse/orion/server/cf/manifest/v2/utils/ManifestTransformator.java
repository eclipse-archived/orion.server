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

import java.util.LinkedList;
import java.util.List;
import org.eclipse.orion.server.cf.manifest.v2.*;

/**
 * Inputs global application properties to manifest applications.
 */
public class ManifestTransformator implements Analyzer {

	/* populate without overriding */
	private void populate(ManifestParseTree application, List<ManifestParseTree> globals) {
		for (ManifestParseTree property : globals)
			if (!application.has(property.getLabel()))
				application.getChildren().add(property);
	}

	@Override
	public void apply(ManifestParseTree node) throws AnalyzerException {

		ManifestParseTree applications = node.getOpt(ManifestConstants.APPLICATIONS);
		if (applications == null)
			/* nothing to do */
			return;

		/* find global application properties */
		List<ManifestParseTree> globals = new LinkedList<ManifestParseTree>();
		for (ManifestParseTree property : node.getChildren())
			if (ManifestUtils.isApplicationProperty(property))
				globals.add(property);

		if (globals.isEmpty())
			/* nothing to do */
			return;

		/* populate properties per application */
		for (ManifestParseTree application : applications.getChildren())
			populate(application, globals);
	}
}