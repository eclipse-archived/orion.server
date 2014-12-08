/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.meterware.httpunit.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * A simple stress test of the Orion server.
 */
public class SimpleServerStressTest extends FileSystemTest {

	@Before
	public void setUp() throws CoreException, IOException, SAXException {
		webConversation = new WebConversation();
		webConversation.setExceptionsThrownOnErrorStatus(false);
		setUpAuthorization();
		createWorkspace();
	}

	@Test
	public void testCreateProject() throws IOException, SAXException, JSONException {
		Performance performance = Performance.getDefault();
		PerformanceMeter meter = performance.createPerformanceMeter("SimpleServerStressTest#testCreateProject");
		final int PROJECT_COUNT = 1000;//increase this value for a real stress test
		meter.start();
		long start = System.currentTimeMillis();
		for (int i = 0; i < PROJECT_COUNT; i++) {
			//create a project
			String projectName = "Project" + i;
			WebRequest request = getCreateProjectRequest(workspaceLocation, projectName, null);
			WebResponse response = webConversation.getResponse(request);
			assertEquals(response.getResponseMessage(), HttpURLConnection.HTTP_CREATED, response.getResponseCode());
			String locationHeader = response.getHeaderField(ProtocolConstants.HEADER_LOCATION);
			assertNotNull(locationHeader);
			if (i % 500 == 0) {
				long end = System.currentTimeMillis();
				long avg = (end - start) / (i + 1);
				System.out.println("Created project " + i + " average time per project: " + avg);
			}
		}
		meter.stop();
		meter.commit();
	}
}
