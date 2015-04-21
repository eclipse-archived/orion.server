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
package org.eclipse.orion.server.tests.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.ParserException;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestParser;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestTransformator;
import org.eclipse.orion.server.cf.manifest.v2.utils.SymbolResolver;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.junit.Test;

public class ManifestParserTest {

	private static String CORRECT_MANIFEST_LOCATION = "testData/manifestTest/correct"; //$NON-NLS-1$
	private static String INCORRECT_MANIFEST_LOCATION = "testData/manifestTest/incorrect"; //$NON-NLS-1$
	private static String INHERITANCE_MANIFEST_LOCATION = "testData/manifestTest/inheritance"; //$NON-NLS-1$
	private static String MANIFEST_LOCATION = "testData/manifestTest"; //$NON-NLS-1$

	@Test
	public void testParserAgainsCorrectManifests() throws Exception {
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(CORRECT_MANIFEST_LOCATION);
		File manifestSource = new File(FileLocator.toFileURL(entry).getPath());

		File[] manifests = manifestSource.listFiles(new FilenameFilter() {

			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".yml"); //$NON-NLS-1$
			}
		});

		for (File manifestFile : manifests) {
			InputStream inputStream = new FileInputStream(manifestFile);

			/* export the manifest and parse the output */
			String exported = exportManifest(inputStream);

			inputStream = new ByteArrayInputStream(exported.getBytes());
			String exportedOutput = exportManifest(inputStream);
			assertEquals(exported, exportedOutput);
		}
	}

	@Test
	public void testParserAgainsIncorrectManifests() throws Exception {
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
				exportManifest(inputStream);
			} catch (IOException ex) {
				failure = true;
			} catch (ParserException ex) {
				failure = true;
			}

			assertTrue(failure);
		}
	}

	@Test
	public void testQuotedManifestProperties() throws Exception {
		String manifestName = "quotedPropertiesManifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		File manifestFile = new File(FileLocator.toFileURL(entry).getPath().concat(manifestName));

		InputStream inputStream = new FileInputStream(manifestFile);
		ManifestParseTree manifest = parse(inputStream);

		ManifestParseTree application = manifest.get("applications").get(0); //$NON-NLS-1$
		ManifestParseTree path = application.get("path"); //$NON-NLS-1$

		assertEquals(".", path.getValue()); //$NON-NLS-1$

		ManifestParseTree host = application.get("host"); //$NON-NLS-1$
		assertEquals("quoted-path-application", host.getValue()); //$NON-NLS-1$

		ManifestParseTree domain = application.get("domain"); //$NON-NLS-1$

		assertEquals("cloud-foundry-domain.org", domain.getValue()); //$NON-NLS-1$
	}

	@Test
	public void testTargetBaseManifestProperties() throws Exception {
		String manifestName = "targetBaseManifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		File manifestFile = new File(FileLocator.toFileURL(entry).getPath().concat(manifestName));

		InputStream inputStream = new FileInputStream(manifestFile);
		ManifestParseTree manifest = parse(inputStream);
		SymbolResolver resolver = new SymbolResolver("api.sauron.mordor.com"); //$NON-NLS-1$
		resolver.apply(manifest);

		assertEquals("api.sauron.mordor.com", manifest.get("applications").get(0).get("domain").getValue()); //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$
		assertTrue(manifest.get("applications").get(0).get("url").getValue().endsWith(".api.sauron.mordor.com")); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
	}

	@Test
	public void testManifestGlobalProperties() throws Exception {
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(INHERITANCE_MANIFEST_LOCATION);

		String manifestName = "01.yml"; //$NON-NLS-1$
		File manifestFile = new File(FileLocator.toFileURL(entry).getPath().concat(manifestName));

		InputStream inputStream = new FileInputStream(manifestFile);
		ManifestParseTree manifest = parse(inputStream);

		ManifestTransformator transformator = new ManifestTransformator();
		transformator.apply(manifest);

		ManifestParseTree applications = manifest.get("applications"); //$NON-NLS-1$
		for (ManifestParseTree application : applications.getChildren())
			assertTrue(application.get("memory").getValue().equals("512M") && application.get("path").getValue().equals(".")); //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$

		manifestName = "02.yml"; //$NON-NLS-1$
		manifestFile = new File(FileLocator.toFileURL(entry).getPath().concat(manifestName));

		inputStream = new FileInputStream(manifestFile);
		manifest = parse(inputStream);

		transformator = new ManifestTransformator();
		transformator.apply(manifest);

		applications = manifest.get("applications"); //$NON-NLS-1$
		assertEquals("512M", applications.get(0).get("memory").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals(".", applications.get(0).get("path").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("1", applications.get(0).get("instances").getValue()); //$NON-NLS-1$//$NON-NLS-2$

		assertEquals("256M", applications.get(1).get("memory").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals(".", applications.get(1).get("path").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("2", applications.get(1).get("instances").getValue()); //$NON-NLS-1$//$NON-NLS-2$

		assertEquals("1024M", applications.get(2).get("memory").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("./app/", applications.get(2).get("path").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("2", applications.get(2).get("instances").getValue()); //$NON-NLS-1$//$NON-NLS-2$

		assertEquals("256M", applications.get(3).get("memory").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("./app/", applications.get(3).get("path").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("1", applications.get(3).get("instances").getValue()); //$NON-NLS-1$//$NON-NLS-2$
	}

	@Test
	public void testServicesWithSpacesManifest() throws Exception {
		String manifestName = "servicesWithSpaces.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		File manifestFile = new File(FileLocator.toFileURL(entry).getPath().concat(manifestName));

		InputStream inputStream = new FileInputStream(manifestFile);
		ManifestParseTree manifest = parse(inputStream);

		ManifestParseTree application = manifest.get("applications").get(0); //$NON-NLS-1$
		ManifestParseTree services = application.get("services"); //$NON-NLS-1$

		assertEquals(2, services.getChildren().size());

		String service = services.get(0).getValue();
		assertEquals("Redis Cloud-fo service", service); //$NON-NLS-1$

		service = services.get(1).getValue();
		assertEquals("Redis-two", service); //$NON-NLS-1$
	}

	private ManifestParseTree parse(InputStream inputStream) throws IOException, ParserException {
		return new ManifestParser().parse(inputStream);
	}

	private String exportManifest(InputStream inputStream) throws IOException, ParserException {
		return parse(inputStream).toString();
	}
}
