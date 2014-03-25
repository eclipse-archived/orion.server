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

import java.util.UUID;
import org.eclipse.orion.server.cf.manifest.v2.*;

/**
 * Resolves supported manifest symbols
 */
public class SymbolResolver implements Analyzer {

	private String targetBase;

	public SymbolResolver(String targetBase) {
		this.targetBase = targetBase;
	}

	@Override
	public void apply(ManifestParseTree node) {

		if (node.getChildren().size() == 0) {

			/* resolve all support symbols */
			for (Token token : node.getTokens())
				if (TokenType.SYMBOL == token.getType())
					resolve(token);

		} else {

			/* resolve symbols in leafs only */
			for (ManifestParseTree child : node.getChildren())
				apply(child);
		}
	}

	private void resolve(Token token) {

		if (targetBase != null && "${target-base}".equals(token.getContent())) { //$NON-NLS-1$
			token.setContent(targetBase);
			return;
		}

		if ("${random-word}".equals(token.getContent())) { //$NON-NLS-1$
			token.setContent(UUID.randomUUID().toString());
			return;
		}
	}
}
