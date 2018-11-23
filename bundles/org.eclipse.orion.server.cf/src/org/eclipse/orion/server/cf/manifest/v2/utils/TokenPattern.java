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

import java.util.regex.Pattern;

public class TokenPattern {
	private Pattern pattern;
	private int type;

	public TokenPattern(String pattern, int type) {
		this.pattern = Pattern.compile("^(" + pattern + ")"); //$NON-NLS-1$//$NON-NLS-2$
		this.type = type;
	}

	public Pattern getPattern() {
		return pattern;
	}

	public int getType() {
		return type;
	}
}
