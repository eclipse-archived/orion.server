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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface Preprocessor {

	/**
	 * Processes the raw input into a list of significant input lines
	 * ready to be processed by the {@link Tokenizer}.
	 * @param inputStream The input stream.
	 * @return List of significant input lines.
	 */
	public List<InputLine> process(InputStream inputStream) throws IOException;

}
