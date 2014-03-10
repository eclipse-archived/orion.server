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

import java.util.NoSuchElementException;

public interface Tokenizer {

	/**
	 * @return <code>true</code> if and only if the 
	 * input token stream is not empty.
	 */
	public boolean hasNext();

	/**
	 * @return The next token in the stream.
	 * 
	 * @throws NoSuchElementException
	 * 		If the input stream has no more elements
	 * 
	 * @throws TokenizerException
	 * 		If the next token could not be parsed, e. g.
	 * 		the input stream contains unsupported characters. 
	 */
	public Token getNextToken() throws TokenizerException;
}