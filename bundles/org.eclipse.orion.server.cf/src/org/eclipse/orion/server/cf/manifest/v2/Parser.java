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
package org.eclipse.orion.server.cf.manifest.v2;

import java.io.InputStream;

public interface Parser {

	/**
	 * @param tokenizer The tokenizer used to parse the manifest file.
	 * @return An intermediate manifest tree representation.
	 * @throws TokenizerException Underlying tokenizer exception.
	 * @throws ParserException Underlying parser exception with additional token information.
	 */
	public ManifestParseTree parse(InputStream inuptStream) throws ParserException;
}
