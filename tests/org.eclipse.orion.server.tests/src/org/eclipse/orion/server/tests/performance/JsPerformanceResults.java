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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.orion.server.tests.performance.JUnitResults.JUnitTestResult;
import org.eclipse.orion.server.tests.performance.JUnitResults.JUnitTestSuite;
import org.eclipse.test.internal.performance.InternalPerformanceMeter;
import org.eclipse.test.internal.performance.PerformanceTestPlugin;
import org.eclipse.test.internal.performance.data.DataPoint;
import org.eclipse.test.internal.performance.data.Dim;
import org.eclipse.test.internal.performance.data.Sample;
import org.eclipse.test.internal.performance.data.Scalar;
import org.eclipse.test.internal.performance.db.DB;
import org.eclipse.test.internal.performance.db.Variations;
import org.eclipse.test.performance.Dimension;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;

public class JsPerformanceResults extends TestCase {

	public static Test suite() {
		String results = System.getProperty("jsPerformanceResults");
		assertNotNull("\"jsPerformanceResults\" system property must be defined", results);

		File resultsFile = new File(results);
		assertTrue(resultsFile.isFile());

		JUnitResults unitResults = new JUnitResults(resultsFile);
		TestSuite testSuite = new TestSuite("JS PerformanceResults");

		for (JUnitTestSuite suite : unitResults.getResults()) {
			TestSuite subSuite = new TestSuite(suite.getSuitePackage());
			for (JUnitTestResult jUnitTestResult : suite.getResults()) {
				subSuite.addTest(new JsPerformanceResults(suite.getSuitePackage(), jUnitTestResult));
			}
			testSuite.addTest(subSuite);
		}
		return testSuite;
	}

	private JUnitTestResult performanceResult;
	private String suiteName;

	public JsPerformanceResults(String suiteName, JUnitTestResult testResult) {
		super(suiteName + "." + testResult.getName());
		this.performanceResult = testResult;
		this.suiteName = suiteName;
	}

	protected void runTest() throws Throwable {
		assertNotNull(performanceResult);
		String scenarioId = performanceResult.getClassName() + "." + performanceResult.getName();

		DataPoint[] points = new DataPoint[2];

		Map<Dimension, Scalar> map = new HashMap<Dimension, Scalar>();
		map.put(Dimension.CPU_TIME, new Scalar((Dim) Dimension.CPU_TIME, 0));
		points[0] = new DataPoint(0, map);

		map = new HashMap<Dimension, Scalar>();
		map.put(Dimension.CPU_TIME, new Scalar((Dim) Dimension.CPU_TIME, (long) performanceResult.getTime() * 1000));
		points[1] = new DataPoint(1, map);

		final Sample sample = new Sample(scenarioId, System.currentTimeMillis(), Collections.EMPTY_MAP, points);
		sample.tagAsSummary(true, performanceResult.getName(), new Dimension[] {Dimension.CPU_TIME}, 0, null);
		Variations variations = PerformanceTestPlugin.getVariations();
		variations.put("browser", suiteName);
		DB.store(variations, sample);

		PerformanceMeter meter = new InternalPerformanceMeter(scenarioId) {
			public void stop() {
				throw new IllegalStateException();
			}

			public void start() {
				throw new IllegalStateException();
			}

			public Sample getSample() {
				return sample;
			}
		};

		Performance perf = Performance.getDefault();
		perf.assertPerformanceInRelativeBand(meter, Dimension.CPU_TIME, -10, 10);
	}
}
