/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.server.tests.performance;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.eclipse.orion.server.tests.performance.JUnitResults.JUnitTestResult;
import org.eclipse.orion.server.tests.performance.JUnitResults.JUnitTestSuite;
import org.eclipse.test.internal.performance.PerformanceTestPlugin;
import org.eclipse.test.internal.performance.data.DataPoint;
import org.eclipse.test.internal.performance.data.Dim;
import org.eclipse.test.internal.performance.data.Sample;
import org.eclipse.test.internal.performance.data.Scalar;
import org.eclipse.test.internal.performance.db.DB;
import org.eclipse.test.internal.performance.db.Variations;
import org.eclipse.test.performance.Dimension;

public class JsPerformanceResults extends TestCase {

	public void testCollectJSResults() throws Exception {
		String results = System.getProperty("jsPerfResults");
		assertNotNull(results);

		File resultsFile = new File(results);
		assertTrue(resultsFile.isFile());

		JUnitResults unitResults = new JUnitResults(resultsFile);

		for (JUnitTestSuite suite : unitResults.getResults()) {
			for (JUnitTestResult jUnitTestResult : suite.getResults()) {
				String scenarioId = jUnitTestResult.getClassName() + "." + jUnitTestResult.getName();

				DataPoint[] points = new DataPoint[2];

				Map<Dimension, Scalar> map = new HashMap<Dimension, Scalar>();
				map.put(Dimension.CPU_TIME, new Scalar((Dim) Dimension.CPU_TIME, 0));
				points[0] = new DataPoint(0, map);

				map = new HashMap<Dimension, Scalar>();
				map.put(Dimension.CPU_TIME, new Scalar((Dim) Dimension.CPU_TIME, (long) jUnitTestResult.getTime() * 1000));
				points[1] = new DataPoint(1, map);

				Sample sample = new Sample(scenarioId, System.currentTimeMillis(), Collections.EMPTY_MAP, points);

				Variations variations = PerformanceTestPlugin.getVariations();
				variations.put("browser", suite.getSuitePackage());
				DB.store(variations, sample);
			}
		}

	}
}
