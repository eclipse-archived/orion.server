/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.patch;

public enum HunkControlChar {
	ADD('+'), REMOVE('-'), CONTEXT(' '), ENDLINE('\\');

	private final char character;

	HunkControlChar(char character) {
		this.character = character;
	}

	public char character() {
		return character;
	}

	public static HunkControlChar valueOf(char character) {
		for (HunkControlChar e : values())
			if (e.character() == character)
				return e;
		throw new IllegalArgumentException("Illegal character: " + character);
	}
}
