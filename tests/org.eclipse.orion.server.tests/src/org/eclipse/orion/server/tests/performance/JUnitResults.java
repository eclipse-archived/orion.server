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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junit.framework.AssertionFailedError;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class JUnitResults {

	public static class JUnitTestResult {
		private String className = null;
		private String name = null;
		private float time = -1;

		public JUnitTestResult(String className, String name, float time) {
			this.className = className;
			this.name = name;
			this.time = time;
		}

		public String getClassName() {
			return className;
		}

		public String getName() {
			return name;
		}

		public float getTime() {
			return time;
		}
	}

	public static class JUnitTestSuite {
		private String suiteName;
		private String suitePackage;
		private int errors;
		private int failures;
		private int tests;
		private float time;
		private List<JUnitTestResult> results = new LinkedList<JUnitTestResult>();

		public JUnitTestSuite(String suiteName, String suitePackage, int errors, int failures, int tests, float time) {
			this.suiteName = suiteName;
			this.suitePackage = suitePackage;
			this.errors = errors;
			this.failures = failures;
			this.tests = tests;
			this.time = time;
		}

		public int getErrorCount() {
			return errors;
		}

		public int getFailureCount() {
			return failures;
		}

		public String getSuiteName() {
			return suiteName;
		}

		public String getSuitePackage() {
			return suitePackage;
		}

		public int getTestCount() {
			return tests;
		}

		public float getTime() {
			return time;
		}

		public void addResult(JUnitTestResult result) {
			results.add(result);
		}

		public List<JUnitTestResult> getResults() {
			return results;
		}
	}

	private List<JUnitTestSuite> suites = new LinkedList<JUnitTestSuite>();

	public JUnitResults(File xmlFile) {
		Document document = null;
		try {
			document = load(xmlFile);
		} catch (ParserConfigurationException e) {
			throw new AssertionFailedError(e.getMessage());
		} catch (SAXException e) {
			throw new AssertionFailedError(e.getMessage());
		} catch (IOException e) {
			throw new AssertionFailedError(e.getMessage());
		}

		parse(document);
	}

	public List<JUnitTestSuite> getResults() {
		return suites;
	}

	private Document load(File file) throws ParserConfigurationException, SAXException, IOException {
		// load the feature xml
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputStream input = new BufferedInputStream(new FileInputStream(file));
		try {
			return builder.parse(input);
		} finally {
			if (input != null)
				try {
					input.close();
				} catch (IOException e) {
					// ignore
				}
		}
	}

	private void parse(Document document) {
		Element element = document.getDocumentElement();
		NodeList nodes = element.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Node suite = nodes.item(i);
			if (suite.getNodeType() != Node.ELEMENT_NODE)
				continue;
			String suiteName = getAttribute(suite, "name");
			String suitePackage = getAttribute(suite, "package");
			int errors = Integer.valueOf(getAttribute(suite, "errors"));
			int failures = Integer.valueOf(getAttribute(suite, "failures"));
			int tests = Integer.valueOf(getAttribute(suite, "tests"));
			float time = Float.valueOf(getAttribute(suite, "time"));

			JUnitTestSuite newSuite = new JUnitTestSuite(suiteName, suitePackage, errors, failures, tests, time);
			suites.add(newSuite);

			NodeList children = suite.getChildNodes();
			for (int j = 0; j < children.getLength(); j++) {
				Node child = children.item(j);
				if (child.getNodeType() != Node.ELEMENT_NODE)
					continue;

				String className = getAttribute(child, "classname");
				String name = getAttribute(child, "name");
				float t = Float.valueOf(getAttribute(child, "time"));

				newSuite.addResult(new JUnitTestResult(className, name, t));
			}
		}
	}

	private String getAttribute(Node node, String name) {
		NamedNodeMap attributes = node.getAttributes();
		if (attributes == null)
			return null;
		Node temp = attributes.getNamedItem(name);
		return temp == null ? null : temp.getNodeValue();
	}
}
