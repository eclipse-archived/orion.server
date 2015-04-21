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

import java.util.UUID;
import java.util.regex.Pattern;

import org.eclipse.orion.server.cf.manifest.v2.Analyzer;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;

/**
 * Resolves supported manifest symbols
 */
public class SymbolResolver implements Analyzer {

	private String targetBase;
	private Pattern SYMBOL_PATTERN = Pattern.compile("\\$\\{[^ \\t\\n\\x0b\\r\\f\\$\\{\\}]+\\} *");

	public SymbolResolver(String targetBase) {
		this.targetBase = targetBase;
	}

	@Override
	public void apply(ManifestParseTree node) {

		if (node.getChildren().size() == 0) {

			/* resolve all support symbols */
			if (SYMBOL_PATTERN.matcher(node.getLabel()).find())
					resolve(node);

		} else {

			/* resolve symbols in leafs only */
			for (ManifestParseTree child : node.getChildren())
				apply(child);
		}
	}

	private void resolve(ManifestParseTree node) {

		if (targetBase != null && node.getLabel().contains("${target-base}")) { //$NON-NLS-1$
			node.setLabel(node.getLabel().replaceAll("\\$\\{target-base\\}", targetBase));
		}

		if (node.getLabel().contains("${random-word}")) { //$NON-NLS-1$
			node.setLabel(node.getLabel().replaceAll("\\$\\{random-word\\}", UUID.randomUUID().toString()));
		}
	}
}
