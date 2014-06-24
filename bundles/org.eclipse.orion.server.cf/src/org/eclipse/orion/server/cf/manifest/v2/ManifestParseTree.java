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
package org.eclipse.orion.server.cf.manifest.v2;

import java.util.ArrayList;
import java.util.List;

/**
 * Intermediate manifest file representation.
 * 
 * Representation details:
 * 1) Line indentation forms a natural forest representation of the manifest.
 *    In order to facilitate accessing single trees in the forest, an additional root node is
 *    introduced in order to bind the forest into a single rooted tree.
 * 2) Tree nodes carry associated token lists.
 */
public class ManifestParseTree {

	private List<Token> tokens;
	private List<ManifestParseTree> children;
	private ManifestParseTree parent;
	private int level;

	public ManifestParseTree() {
		children = new ArrayList<ManifestParseTree>();
		tokens = new ArrayList<Token>();
		parent = this;
		level = 0;
	}

	public ManifestParseTree(int level) {
		children = new ArrayList<ManifestParseTree>();
		tokens = new ArrayList<Token>();
		parent = this;
		this.level = level;
	}

	/**
	 * @return Token content concatenation.
	 */
	public String getLabel() {
		StringBuilder sb = new StringBuilder();
		int n = tokens.size();
		for (int i = 0; i < n; ++i) {
			Token token = tokens.get(i);
			sb.append(token.getContent());

			if (TokenType.SYMBOL != token.getType() && (i + 1 < n && TokenType.SYMBOL != tokens.get(i + 1).getType()))
				sb.append(" "); //$NON-NLS-1$
		}

		return sb.toString().trim();
	}

	/**
	 * Access helper method. Should be used for named children only.
	 * @param childName Name of the child to be retrieved.
	 * @return The first child node matching the childName label.
	 * @throws InvalidAccessException If no matching child could be found.
	 */
	public ManifestParseTree get(String childName) throws InvalidAccessException {
		/* TODO: Consider constant (or amortized constant) 
		 * time access using additional memory. */

		for (ManifestParseTree child : children)
			if (childName.equals(child.getLabel()))
				return child;

		throw new InvalidAccessException(this, childName);
	}

	/**
	 * Access helper method. Should be used for named children only.
	 * @param childName Name of the child to be retrieved.
	 * @return The first child node matching the childName label or <code>null</code> if none present.
	 */
	public ManifestParseTree getOpt(String childName) {
		/* TODO: Consider constant (or amortized constant) 
		 * time access using additional memory. */

		for (ManifestParseTree child : children)
			if (childName.equals(child.getLabel()))
				return child;

		return null;
	}

	/**
	 * Tests whether the named child exists or not.
	 * @param childName Name of the child to be tested.
	 * @return <code>true</code> if and only if the given child exists.
	 */
	public boolean has(String childName) {
		return getOpt(childName) != null;
	}

	/**
	 * Access helper method. Should be used for item children only.
	 * @param kthChild Number of the item child to be retrieved, listed from 0.
	 * @return The kth item child node.
	 * @throws InvalidAccessException If no matching child could be found.
	 */
	public ManifestParseTree get(int kthChild) throws InvalidAccessException {
		/* TODO: Consider constant (or amortized constant) 
		 * time access using additional memory. */

		int curretChild = -1;
		for (ManifestParseTree child : children) {
			if (child.isItemNode()) {
				++curretChild;

				if (curretChild == kthChild)
					return child;
			}
		}

		throw new InvalidAccessException(this, kthChild);
	}

	/**
	 * Access helper method. Should be used for (key:value) mappings only.
	 * Removes any starting or ending quotation marks, i.e. " and '.
	 * @return Label of the first child.
	 * @throws InvalidAccessException If the nodes has no children.
	 */
	public String getValue() throws InvalidAccessException {
		if (children.isEmpty())
			throw new InvalidAccessException(this);

		return children.get(0).getLabel().replaceAll("^\"|^\'|\"$|\'$", ""); //$NON-NLS-1$ //$NON-NLS-2$;
	}

	/**
	 * @return <code>true</code> if and only if the node represents a list.
	 */
	public boolean isList() {
		if (children.isEmpty())
			return false;

		return children.get(0).isItemNode();
	}

	/**
	 * Externalization helper method
	 */
	protected String toString(int indentation) {
		StringBuilder sb = new StringBuilder();

		if (getParent() == this) {

			/* special case: manifest root */
			sb.append("---" + System.getProperty("line.separator")); //$NON-NLS-1$ //$NON-NLS-2$
			for (ManifestParseTree child : children)
				sb.append(child.toString(0) + System.getProperty("line.separator")); //$NON-NLS-1$

			return sb.toString();
		}

		/* print indentation */
		for (int i = 0; i < indentation; ++i)
			sb.append(" "); //$NON-NLS-1$

		/* print tokens */
		sb.append(getLabel());

		/* print mapping symbol if required */
		boolean isItemNode = (tokens.size() == 1 && TokenType.ITEM_CONSTANT == tokens.get(0).getType());
		if (!isItemNode && children.size() > 0)
			sb.append(":"); //$NON-NLS-1$

		/* print children nodes */
		int childrenSize = children.size();
		for (int i = 0; i < childrenSize; ++i) {
			ManifestParseTree child = children.get(i);

			if ((isItemNode && i == 0) || (childrenSize == 1 && child.getChildren().size() == 0)) {
				/* special case: in-line item */
				sb.append(" "); //$NON-NLS-1$
				sb.append(child.toString(0));
			} else {
				sb.append(System.getProperty("line.separator")); //$NON-NLS-1$

				if (!child.isItemNode())
					sb.append(child.toString(indentation + 2));
				else
					sb.append(child.toString(indentation));
			}
		}

		return sb.toString();
	}

	public boolean isRoot() {
		return parent == this;
	}

	public boolean isItemNode() {
		return (tokens.size() == 1 && TokenType.ITEM_CONSTANT == tokens.get(0).getType());
	}

	public List<ManifestParseTree> getChildren() {
		return children;
	}

	public ManifestParseTree getParent() {
		return parent;
	}

	public void setParent(ManifestParseTree father) {
		this.parent = father;
	}

	public int getLevel() {
		return level;
	}

	public List<Token> getTokens() {
		return tokens;
	}

	@Override
	public String toString() {
		return toString(0);
	}
}