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

public class InputLine {

	private String content;
	private int lineNumber;

	public InputLine(String content, int lineNumber) {
		this.content = content;
		this.lineNumber = lineNumber;
	}

	public String getContent() {
		return content;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}

	@Override
	public String toString() {
		return String.valueOf(lineNumber) + ": " + content; //$NON-NLS-1$
	}
}
