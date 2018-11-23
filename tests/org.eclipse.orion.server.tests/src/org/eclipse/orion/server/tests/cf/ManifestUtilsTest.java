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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.orion.server.cf.manifest.v2.AnalyzerException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.junit.Test;

public class ManifestUtilsTest {

	@Test
	public void testSingleInheritancePropagation() throws Exception {
		String MANIFEST_LOCATION = "testData/manifestTest/inheritance/01"; //$NON-NLS-1$
		String manifestName = "prod-manifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		IFileStore fs = EFS.getStore(URIUtil.toURI(FileLocator.toFileURL(entry).getPath().concat(manifestName)));

		ManifestParseTree manifest = ManifestUtils.parse(fs.getParent(), fs);
		ManifestParseTree applications = manifest.get("applications"); //$NON-NLS-1$
		assertEquals(4, applications.getChildren().size());

		for (ManifestParseTree application : applications.getChildren()) {
			assertTrue(application.has("domain")); //$NON-NLS-1$
			assertTrue(application.has("instances")); //$NON-NLS-1$
			assertTrue(application.has("path")); //$NON-NLS-1$
			assertTrue(application.has("memory")); //$NON-NLS-1$
		}
	}

	@Test
	public void testSingleRelativeInheritance() throws Exception {
		String MANIFEST_LOCATION = "testData/manifestTest/inheritance/02/inner"; //$NON-NLS-1$
		String manifestName = "prod-manifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		IFileStore fs = EFS.getStore(URIUtil.toURI(FileLocator.toFileURL(entry).getPath().concat(manifestName)));

		ManifestParseTree manifest = ManifestUtils.parse(fs.getParent().getParent(), fs);
		ManifestParseTree applications = manifest.get("applications"); //$NON-NLS-1$
		assertEquals(2, applications.getChildren().size());

		for (ManifestParseTree application : applications.getChildren()) {
			assertTrue(application.has("domain")); //$NON-NLS-1$
			assertTrue(application.has("instances")); //$NON-NLS-1$
			assertTrue(application.has("path")); //$NON-NLS-1$
			assertTrue(application.has("memory")); //$NON-NLS-1$
		}
	}

	@Test
	public void testFlatSingleRelativeInheritance() throws Exception {
		String MANIFEST_LOCATION = "testData/manifestTest/inheritance/03"; //$NON-NLS-1$
		String manifestName = "prod-manifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		IFileStore fs = EFS.getStore(URIUtil.toURI(FileLocator.toFileURL(entry).getPath().concat(manifestName)));

		ManifestParseTree manifest = ManifestUtils.parse(fs.getParent(), fs);

		ManifestParseTree applications = manifest.get("applications"); //$NON-NLS-1$
		assertEquals(1, applications.getChildren().size());

		ManifestParseTree application = applications.get(0);

		assertEquals("512M", application.get("memory").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("2", application.get("instances").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("example.com", application.get("domain").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals(".", application.get("path").getValue()); //$NON-NLS-1$//$NON-NLS-2$
	}

	@Test
	public void testInnerSingleRelativeInheritance() throws Exception {
		String MANIFEST_LOCATION = "testData/manifestTest/inheritance/04"; //$NON-NLS-1$
		String manifestName = "prod-manifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		IFileStore fs = EFS.getStore(URIUtil.toURI(FileLocator.toFileURL(entry).getPath().concat(manifestName)));

		ManifestParseTree manifest = ManifestUtils.parse(fs.getParent(), fs);
		ManifestParseTree applications = manifest.get("applications"); //$NON-NLS-1$
		assertEquals(2, applications.getChildren().size());
	}

	@Test(expected = AnalyzerException.class)
	public void testSingleInheritanceOutsideSandbox() throws Exception {
		String MANIFEST_LOCATION = "testData/manifestTest/inheritance/05"; //$NON-NLS-1$
		String manifestName = "prod-manifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		IFileStore fs = EFS.getStore(URIUtil.toURI(FileLocator.toFileURL(entry).getPath().concat(manifestName)));
		ManifestUtils.parse(fs.getParent(), fs);
	}

	@Test
	public void testFlatComplexInheritance() throws Exception {
		String MANIFEST_LOCATION = "testData/manifestTest/inheritance/06"; //$NON-NLS-1$
		String manifestName = "final-manifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		IFileStore fs = EFS.getStore(URIUtil.toURI(FileLocator.toFileURL(entry).getPath().concat(manifestName)));

		ManifestParseTree manifest = ManifestUtils.parse(fs.getParent(), fs);
		ManifestParseTree applications = manifest.get("applications"); //$NON-NLS-1$
		assertEquals(2, applications.getChildren().size());

		for (ManifestParseTree application : applications.getChildren()) {
			assertTrue(application.has("domain")); //$NON-NLS-1$
			assertTrue(application.has("instances")); //$NON-NLS-1$
			assertTrue(application.has("memory")); //$NON-NLS-1$

			if ("A".equals(application.get("name").getValue())) //$NON-NLS-1$ //$NON-NLS-2$
				assertTrue("2".equals(application.get("instances").getValue())); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@Test(expected = AnalyzerException.class)
	public void testInheritanceCycle() throws Exception {
		String MANIFEST_LOCATION = "testData/manifestTest/inheritance/07"; //$NON-NLS-1$
		String manifestName = "final-manifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		IFileStore fs = EFS.getStore(URIUtil.toURI(FileLocator.toFileURL(entry).getPath().concat(manifestName)));
		ManifestUtils.parse(fs.getParent(), fs);
	}

	@Test
	public void testComplexInheritance() throws Exception {
		String MANIFEST_LOCATION = "testData/manifestTest/inheritance/08/A/inner"; //$NON-NLS-1$
		String manifestName = "final-manifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		IFileStore fs = EFS.getStore(URIUtil.toURI(FileLocator.toFileURL(entry).getPath().concat(manifestName)));

		ManifestParseTree manifest = ManifestUtils.parse(fs.getParent().getParent().getParent(), fs);
		ManifestParseTree applications = manifest.get("applications"); //$NON-NLS-1$
		assertEquals(2, applications.getChildren().size());

		for (ManifestParseTree application : applications.getChildren()) {
			assertTrue(application.has("domain")); //$NON-NLS-1$
			assertTrue(application.has("instances")); //$NON-NLS-1$
			assertTrue(application.has("memory")); //$NON-NLS-1$

			if ("A".equals(application.get("name").getValue())) //$NON-NLS-1$ //$NON-NLS-2$
				assertTrue("2".equals(application.get("instances").getValue())); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@Test
	public void testEnvInheritance() throws Exception {
		String MANIFEST_LOCATION = "testData/manifestTest/inheritance/09"; //$NON-NLS-1$
		String manifestName = "prod-manifest.yml"; //$NON-NLS-1$

		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(MANIFEST_LOCATION);
		IFileStore fs = EFS.getStore(URIUtil.toURI(FileLocator.toFileURL(entry).getPath().concat(manifestName)));

		ManifestParseTree manifest = ManifestUtils.parse(fs.getParent(), fs);

		ManifestParseTree applications = manifest.get("applications"); //$NON-NLS-1$
		assertEquals(1, applications.getChildren().size());

		ManifestParseTree env = manifest.get("env"); //$NON-NLS-1$
		assertEquals(2, env.getChildren().size());

		assertEquals("base", env.get("TEST").getValue()); //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("overridden", env.get("TEST2").getValue()); //$NON-NLS-1$//$NON-NLS-2$
	}
}
