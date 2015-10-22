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

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.orion.server.cf.utils.PackageUtils;
import org.eclipse.orion.server.tests.ServerTestsActivator;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

public class PackagerTest {

	/*
	 * Bypass ZipEntry.equals by custom argument matcher using
	 * zip entry name as equality criteria.
	 */
	private class ZipArgumentMatcher extends ArgumentMatcher<ZipEntry> {
		ZipEntry entry;

		public ZipArgumentMatcher(ZipEntry thisObject) {
			this.entry = thisObject;
		}

		@Override
		public boolean matches(Object argument) {
			if (argument instanceof ZipEntry) {
				ZipEntry arg = (ZipEntry) argument;
				return entry.getName().equals(arg.getName());
			}

			return false;
		}
	}

	@Test
	public void testCFIgnoreRules() throws Exception {

		ZipOutputStream mockZos = mock(ZipOutputStream.class);

		String LOCATION = "testData/packagerTest/01"; //$NON-NLS-1$
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(LOCATION);
		IFileStore source = EFS.getStore(URIUtil.toURI(FileLocator.toFileURL(entry).getPath()));

		PackageUtils.writeZip(source, mockZos);

		/* what is... */
		verify(mockZos).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry("test2.in")))); //$NON-NLS-1$
		verify(mockZos).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry(".cfignore")))); //$NON-NLS-1$
		verify(mockZos).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry("A")))); //$NON-NLS-1$
		verify(mockZos).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry("inner/test2.in")))); //$NON-NLS-1$
		verify(mockZos).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry("inner/inner2/test3.in")))); //$NON-NLS-1$

		/* ... and what should never be */
		verify(mockZos, never()).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry("test1.in")))); //$NON-NLS-1$
		verify(mockZos, never()).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry("inner/test1.in")))); //$NON-NLS-1$
		verify(mockZos, never()).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry("inner/inner2/inner3/test2.in")))); //$NON-NLS-1$
	}

	@Test
	public void testCFIgnoreNegation() throws Exception {
		ZipOutputStream mockZos = mock(ZipOutputStream.class);

		String LOCATION = "testData/packagerTest/02"; //$NON-NLS-1$
		URL entry = ServerTestsActivator.getContext().getBundle().getEntry(LOCATION);
		IFileStore source = EFS.getStore(URIUtil.toURI(FileLocator.toFileURL(entry).getPath()));

		PackageUtils.writeZip(source, mockZos);

		/* what is... */
		verify(mockZos).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry(".cfignore")))); //$NON-NLS-1$
		verify(mockZos).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry("A/B/.cfignore")))); //$NON-NLS-1$
//		verify(mockZos).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry("A/B/test.in")))); //$NON-NLS-1$

		/* ... and what should never be */
		verify(mockZos, never()).putNextEntry(argThat(new ZipArgumentMatcher(new ZipEntry("A/B/test2.in")))); //$NON-NLS-1$
	}
}