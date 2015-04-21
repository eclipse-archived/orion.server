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
package org.eclipse.orion.server.cf.manifest.v2.utils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.Parser;
import org.eclipse.orion.server.cf.manifest.v2.ParserException;
import org.eclipse.osgi.util.NLS;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

public class ManifestParser implements Parser {

	@Override
	public ManifestParseTree parse(InputStream inputStream) throws ParserException {
		Node snakeRootNode;
		try {
			snakeRootNode = new Yaml().compose(new InputStreamReader(inputStream));
		} catch (MarkedYAMLException e) {
			throw new ParserException(e.getMessage(), e.getProblemMark().getLine() + 1);
		}
		ManifestParseTree root = new ManifestParseTree();
		addChild(snakeRootNode, root);
		return root;
	}

	private void addChild(NodeTuple tuple, ManifestParseTree parent) throws ParserException {
		ManifestParseTree keyNode = new ManifestParseTree();
		Node tupleKeyNode = tuple.getKeyNode();
		int lineNumber = tupleKeyNode.getStartMark().getLine() + 1;
		if (tupleKeyNode.getNodeId() != NodeId.scalar) {
			throw new ParserException(NLS.bind(ManifestConstants.UNSUPPORTED_TOKEN_ERROR, lineNumber), lineNumber);
		}
		keyNode.setLabel(((ScalarNode) tupleKeyNode).getValue());
		keyNode.setLineNumber(lineNumber);
		keyNode.setParent(parent);
		parent.getChildren().add(keyNode);
		addChild(tuple.getValueNode(), keyNode);
	}

	private void addChild(Node snakeNode, ManifestParseTree parent) throws ParserException {
		switch (snakeNode.getNodeId()) {
		case sequence:
			List<Node> children = ((SequenceNode) snakeNode).getValue();
			for (Node child : children) {
				ManifestParseTree itemNode = new ManifestParseTree();
				itemNode.setItemNode(true);
				itemNode.setLabel("-");
				itemNode.setLineNumber(child.getStartMark().getLine() + 1);

				itemNode.setParent(parent);
				parent.getChildren().add(itemNode);

				addChild(child, itemNode);
			}
			break;
		case mapping:
			List<NodeTuple> toupleChildren = ((MappingNode) snakeNode).getValue();
			for (NodeTuple child : toupleChildren) {
				addChild(child, parent);
			}
			break;
		case scalar:
			ManifestParseTree newNode = new ManifestParseTree();
			newNode.setLabel(((ScalarNode) snakeNode).getValue());
			newNode.setLineNumber(snakeNode.getStartMark().getLine() + 1);

			newNode.setParent(parent);
			parent.getChildren().add(newNode);
			break;
		default:
			break;
		}
	}

}
