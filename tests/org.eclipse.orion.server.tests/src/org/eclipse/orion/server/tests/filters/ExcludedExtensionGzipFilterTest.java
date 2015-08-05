/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.servlets.ExcludedExtensionGzipFilter;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.junit.Before;
import org.junit.Test;

import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * Tests for {@link ExcludedExtensionGzipFilter}.
 */
public class ExcludedExtensionGzipFilterTest extends FileSystemTest {
	private static final String CONTENT_ENCODING = "Content-Encoding";

	@Before
	public void setUp() throws Exception {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
		createWorkspace(SimpleMetaStore.DEFAULT_WORKSPACE_NAME);
		createTestProject(testName.getMethodName());
	}

	@Test
	public void testExcludedExtensions() throws Exception {
		assertGzipped(getFileResponse("/file/foo.txt"));
		assertGzipped(getFileResponse("/file/bar"));

		assertNotGzipped(getFileResponse("/file/baz.gif"));
		assertNotGzipped(getFileResponse("/file/qux.png"));
	}

	private static void assertGzipped(WebResponse res) throws Exception {
		assertEquals("gzip", res.getHeaderField(CONTENT_ENCODING));
	}

	private static void assertNotGzipped(WebResponse res) throws Exception {
		assertNotEquals("gzip", res.getHeaderField(CONTENT_ENCODING));
	}

	private WebResponse getFileResponse(String location) throws Exception {
		WebRequest request = getGetFilesRequest(location);
		WebResponse response = webConversation.getResponse(request);
		return response;
	}

}
