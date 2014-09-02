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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.orion.server.cf.manifest.v2.InputLine;
import org.eclipse.orion.server.cf.manifest.v2.Preprocessor;

/**
 * Reads the specified manifest file and processes the input by:
 * 1. Removing all in-line comments,
 * 2. Discarding non-significant input lines.
 */
public class ManifestPreprocessor implements Preprocessor {

	@Override
	public List<InputLine> process(InputStream inputStream) throws IOException {
		BufferedReader reader = null;

		try {
			reader = new BufferedReader(new InputStreamReader(inputStream));
			List<InputLine> contents = new ArrayList<InputLine>();

			String line = null;
			int currentLine = 0;

			while ((line = reader.readLine()) != null) {
				++currentLine;

				line = processLine(line);
				if (line.isEmpty() || line.trim().isEmpty())
					continue;

				InputLine inputLine = new InputLine(line, currentLine);
				contents.add(inputLine);
			}

			return contents;

		} finally {
			if (reader != null)
				reader.close();
		}
	}

	private String processLine(String inputLine) {
		/* ignore input comments */
		int hashIdx = inputLine.indexOf('#');
		String tmp = (hashIdx != -1) ? inputLine.substring(0, hashIdx) : inputLine;

		/* ignore the opening manifest token '---' */
		return (!tmp.trim().equals("---")) ? tmp : ""; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
