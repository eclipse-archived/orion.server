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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.cf.manifest.v2.*;

public class ManifestUtils {

	/**
	 * Utility method wrapping the manifest parse process with additional semantic analysis.
	 * @param manifestFileStore Manifest file store used to fetch the manifest contents.
	 * @param targetBase Cloud foundry target base used to resolve manifest symbols.
	 * @return An intermediate manifest tree representation.
	 * @throws CoreException If the manifest store input stream could not be opened.
	 * @throws IOException If the manifest store could not be closed successfully.
	 * @throws TokenizerException If the manifest tokenizer failed to tokenize the input.
	 * @throws ParserException If the manifest parser failed to parse the input.
	 */
	public static ManifestParseTree parse(IFileStore manifestFileStore, String targetBase) throws CoreException, IOException, TokenizerException, ParserException {

		InputStream inputStream = manifestFileStore.openInputStream(EFS.NONE, null);

		/* run preprocessor */
		ManifestPreprocessor preprocessor = new ManifestPreprocessor();
		List<InputLine> inputLines = preprocessor.process(inputStream);

		/* run parser */
		ManifestTokenizer tokenizer = new ManifestTokenizer(inputLines);
		ManifestParser parser = new ManifestParser();
		ManifestParseTree parseTree = parser.parse(tokenizer);

		/* resolve symbols */
		if (targetBase != null) {
			SymbolResolver symbolResolver = new SymbolResolver(targetBase);
			symbolResolver.apply(parseTree);
		}

		return parseTree;
	}

	public static ManifestParseTree parse(IFileStore manifestFileStore) throws CoreException, IOException, TokenizerException, ParserException {
		return parse(manifestFileStore, null);
	}

}