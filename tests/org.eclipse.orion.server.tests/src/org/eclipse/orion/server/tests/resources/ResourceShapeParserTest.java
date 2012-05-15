/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.resources;

import static org.junit.Assert.assertEquals;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.Tree;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.ResourceShapeLexer;
import org.eclipse.orion.server.core.resources.ResourceShapeParser;
import org.eclipse.orion.server.core.resources.ResourceShapeParser.parse_return;
import org.junit.Test;

/**
 * Tests for {@link ResourceShapeParser}.
 */
public class ResourceShapeParserTest {

	@Test(expected = IllegalArgumentException.class)
	public void testSingleSeparator() throws Exception {
		// given
		ResourceShapeParser parser = createResourceShapeParser(",");

		// when
		parser.parse();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testLowercaseName() throws Exception {
		// given
		ResourceShapeParser parser = createResourceShapeParser(TestResource.STRING_PROPERTY_NAME + ResourceShape.SEPARATOR + "name");

		// when
		parser.parse();
	}

	@Test
	public void testSingleProperty() throws Exception {
		// given
		ResourceShapeParser parser = createResourceShapeParser(TestResource.STRING_PROPERTY_NAME);

		// when
		parse_return parsed = parser.parse();

		// then
		CommonTree tree = (CommonTree) parsed.getTree();
		assertEquals(ResourceShapeParser.tokenNames[ResourceShapeParser.PROPERTIES], tree.getText());
		assertEquals(1, tree.getChildCount());
		assertEquals(TestResource.STRING_PROPERTY_NAME, tree.getChild(0).getText());
	}

	@Test
	public void testCommaSeparatedProperties() throws Exception {
		// given
		ResourceShapeParser parser = createResourceShapeParser(TestResource.STRING_PROPERTY_NAME + ResourceShape.SEPARATOR + TestResource.BOOLEAN_PROPERTY_NAME);

		// when
		parse_return parsed = parser.parse();

		// then
		CommonTree tree = (CommonTree) parsed.getTree();
		assertEquals(ResourceShapeParser.tokenNames[ResourceShapeParser.PROPERTIES], tree.getText());
		assertEquals(2, tree.getChildCount());
		assertEquals(TestResource.STRING_PROPERTY_NAME, tree.getChild(0).getText());
		assertEquals(TestResource.BOOLEAN_PROPERTY_NAME, tree.getChild(1).getText());
	}

	@Test
	public void testNestedResource() throws Exception {
		// given
		ResourceShapeParser parser = createResourceShapeParser(TestResource.STRING_PROPERTY_NAME + ResourceShape.SEPARATOR + TestResource.RESOURCE_PROPERTY_NAME);

		// when
		parse_return parsed = parser.parse();

		// then
		CommonTree tree = (CommonTree) parsed.getTree();
		assertEquals(ResourceShapeParser.tokenNames[ResourceShapeParser.PROPERTIES], tree.getText());
		assertEquals(2, tree.getChildCount());
		assertEquals(TestResource.STRING_PROPERTY_NAME, tree.getChild(0).getText());
		assertEquals(TestResource.RESOURCE_PROPERTY_NAME, tree.getChild(1).getText());
	}

	@Test
	public void testNestedResourceWithProperties() throws Exception {
		// given
		ResourceShapeParser parser = createResourceShapeParser(TestResource.RESOURCE_PROPERTY_NAME + "{" + TestResource.BOOLEAN_PROPERTY_NAME + ResourceShape.SEPARATOR + TestResource.INT_PROPERTY_NAME + "}");

		// when
		parse_return parsed = parser.parse();

		// then
		Tree tree = (CommonTree) parsed.getTree();
		assertEquals(ResourceShapeParser.tokenNames[ResourceShapeParser.PROPERTIES], tree.getText());
		assertEquals(1, tree.getChildCount());
		tree = tree.getChild(0);
		assertEquals(ResourceShapeParser.tokenNames[ResourceShapeParser.NESTED_PROPERTIES], tree.getText());
		assertEquals(2, tree.getChildCount());
		assertEquals(TestResource.RESOURCE_PROPERTY_NAME, tree.getChild(0).getText());
		assertEquals(ResourceShapeParser.tokenNames[ResourceShapeParser.PROPERTIES], tree.getChild(1).getText());
		tree = tree.getChild(1);
		assertEquals(2, tree.getChildCount());
		assertEquals(TestResource.BOOLEAN_PROPERTY_NAME, tree.getChild(0).getText());
		assertEquals(TestResource.INT_PROPERTY_NAME, tree.getChild(1).getText());
	}

	@Test
	public void testAllPropertiesForNestedResource() throws Exception {
		// given
		ResourceShapeParser parser = createResourceShapeParser(TestResource.RESOURCE_PROPERTY_NAME + "{" + ResourceShape.WILDCARD + "}");

		// when
		parse_return parsed = parser.parse();

		// then
		Tree tree = (CommonTree) parsed.getTree();
		assertEquals(ResourceShapeParser.tokenNames[ResourceShapeParser.PROPERTIES], tree.getText());
		assertEquals(1, tree.getChildCount());
		tree = tree.getChild(0);
		assertEquals(ResourceShapeParser.tokenNames[ResourceShapeParser.NESTED_PROPERTIES], tree.getText());
		assertEquals(2, tree.getChildCount());
		assertEquals(TestResource.RESOURCE_PROPERTY_NAME, tree.getChild(0).getText());
		assertEquals(ResourceShapeParser.tokenNames[ResourceShapeParser.PROPERTIES], tree.getChild(1).getText());
		tree = tree.getChild(1);
		assertEquals(1, tree.getChildCount());
		assertEquals("*" /*ResourceShapeParser.tokenNames[ResourceShapeParser.WILDCARD]*/, tree.getChild(0).getText());
	}

	private static ResourceShapeParser createResourceShapeParser(String s) {
		ANTLRStringStream in = new ANTLRStringStream(s);
		ResourceShapeLexer lexer = new ResourceShapeLexer(in);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		return new ResourceShapeParser(tokens);
	}
}
