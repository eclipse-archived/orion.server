/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.manifest;

import java.util.LinkedList;
import java.util.Scanner;

public class ManifestParser {

	/**
	 * Determines the node father.
	 */
	private static ManifestNode getFather(LinkedList<ManifestNode> nodes, ManifestNode node) {
		for (ManifestNode father : nodes)
			if (father.getDepth() < node.getDepth())
				return father;

		return null;
	}

	/**
	 * Parses a manifest token tree based on the input indentation.
	 * Note: not all manifest formats are supported.
	 */
	public static ManifestNode parse(Scanner scanner) throws ParseException {

		/* scan the manifest root */
		String input = null;

		while (scanner.hasNextLine()) {
			input = ManifestUtils.removeComments(scanner.nextLine());

			/* ignore the (possibly) initial '---' line */
			if ("---".equals(input.trim()))
				continue;

			if (input.length() > 0)
				break;
		}

		if (input == null || input.length() == 0)
			throw new ParseException("Corrupted or unsupported manifest format");

		int depth = ManifestUtils.getDepth(input);

		ManifestNode tree = new ManifestNode(input, depth);
		LinkedList<ManifestNode> nodeList = new LinkedList<ManifestNode>();
		nodeList.addFirst(tree);

		while (scanner.hasNextLine()) {
			input = ManifestUtils.removeComments(scanner.nextLine());
			depth = ManifestUtils.getDepth(input);

			if (input.length() == 0)
				continue;

			if (ManifestUtils.isSequenceItem(input))
				depth = depth + 2;

			/* attach to last node with strictly smaller depth */
			ManifestNode node = new ManifestNode(input, depth);
			ManifestNode father = getFather(nodeList, node);
			if (father == null)
				throw new ParseException("Corrupted or unsupported manifest format");

			father.getChildren().add(node);
			nodeList.addFirst(node);
		}

		return tree;
	}
}
