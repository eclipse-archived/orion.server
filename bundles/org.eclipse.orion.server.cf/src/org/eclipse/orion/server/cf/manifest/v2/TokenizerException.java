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
package org.eclipse.orion.server.cf.manifest.v2;

import org.eclipse.osgi.util.NLS;

public class TokenizerException extends Exception {
	private static final long serialVersionUID = 1L;

	private String message;
	private InputLine line;

	public TokenizerException(String message) {
		this.message = message;
		this.line = null;
	}

	public TokenizerException(String message, InputLine line) {
		this.message = message;
		this.line = line;
	}

	@Override
	public String getMessage() {
		if (line == null)
			/* general tokenizer exception */
			return message;
		else
			return NLS.bind(message, line.getLineNumber(), line.getContent());
	}
}