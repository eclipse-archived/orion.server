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
package org.eclipse.orion.server.tests.cf;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.orion.server.cf.manifest.v2.InputLine;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.Parser;
import org.eclipse.orion.server.cf.manifest.v2.ParserException;
import org.eclipse.orion.server.cf.manifest.v2.Preprocessor;
import org.eclipse.orion.server.cf.manifest.v2.Tokenizer;
import org.eclipse.orion.server.cf.manifest.v2.TokenizerException;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestParser;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestPreprocessor;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestTokenizer;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class ManifestParseTreeTest {

	private static String CORRECT_MANIFEST_LOCATION = "testData/manifestTest/correct"; //$NON-NLS-1$
	private static String INCORRECT_MANIFEST_LOCATION = "testData/manifestTest/incorrect"; //$NON-NLS-1$

	private ManifestParseTree parse(InputStream inputStream) throws IOException, TokenizerException, ParserException {
		Preprocessor preprocessor = new ManifestPreprocessor();
		List<InputLine> contents = preprocessor.process(inputStream);
		Tokenizer tokenizer = new ManifestTokenizer(contents);

		Parser parser = new ManifestParser();
		return parser.parse(tokenizer);
	}

	private JSONObject exportManifestJSON(InputStream inputStream) throws IOException, TokenizerException, ParserException, JSONException, InvalidAccessException {
		return parse(inputStream).toJSON();
	}

	@Test
	public void testToJSONAgainsCorrectManifests() throws Exception {
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(CORRECT_MANIFEST_LOCATION);
		File manifestSource = new File(FileLocator.toFileURL(entry).getPath());

		File[] manifests = manifestSource.listFiles(new FilenameFilter() {

			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".yml"); //$NON-NLS-1$
			}
		});

		for (File manifestFile : manifests) {
			InputStream inputStream = new FileInputStream(manifestFile);

			/* export the manifest to JSON - pass if no exceptions occurred */
			exportManifestJSON(inputStream);
		}
	}

	@Test
	public void testToJSONAgainsIncorrectManifests() throws Exception {
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(INCORRECT_MANIFEST_LOCATION);
		File manifestSource = new File(FileLocator.toFileURL(entry).getPath());

		File[] manifests = manifestSource.listFiles(new FilenameFilter() {

			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".yml"); //$NON-NLS-1$
			}
		});

		boolean failure = false;
		for (File manifestFile : manifests) {
			failure = false;
			InputStream inputStream = new FileInputStream(manifestFile);

			/* export the manifest */
			try {
				exportManifestJSON(inputStream);
			} catch (IOException ex) {
				failure = true;
			} catch (TokenizerException ex) {
				failure = true;
			} catch (ParserException ex) {
				failure = true;
			} catch (JSONException ex) {
				failure = true;
			} catch (InvalidAccessException ex) {
				failure = true;
			}

			assertTrue(failure);
		}
	}

	@Test
	public void testFromJSONAgainsCorrectManifests() throws Exception {
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(CORRECT_MANIFEST_LOCATION);
		File manifestSource = new File(FileLocator.toFileURL(entry).getPath());

		File[] manifests = manifestSource.listFiles(new FilenameFilter() {

			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".yml"); //$NON-NLS-1$
			}
		});

		for (File manifestFile : manifests) {
			InputStream inputStream = new FileInputStream(manifestFile);

			/* export the manifest to JSON - pass if no exceptions occurred */
			JSONObject manifestJSON = exportManifestJSON(inputStream);
			ManifestUtils.parse(manifestJSON).toString();
		}
	}
}
