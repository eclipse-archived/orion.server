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

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.IOUtilities;
import org.json.*;

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

	private static final Pattern memoryPattern = Pattern.compile("[1-9][0-9]*(M|MB|G|GB|m|mb|g|gb)"); //$NON-NLS-1$
	private static final Pattern nonNegativePattern = Pattern.compile("[1-9][0-9]*"); //$NON-NLS-1$

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

	public ManifestParseTree(ManifestParseTree node) {

		level = node.getLevel();
		setParent(this);

		/* copy tokens */
		tokens = new ArrayList<Token>();
		for (Token token : node.getTokens()) {
			Token newToken = new Token(token.getContent(), token.getType());
			newToken.setIndentation(token.getIndentation());
			newToken.setLineNumber(token.getLineNumber());
			tokens.add(newToken);
		}

		/* copy children recursively */
		children = new ArrayList<ManifestParseTree>();
		for (ManifestParseTree child : node.getChildren()) {
			ManifestParseTree newChild = new ManifestParseTree(child);
			newChild.setParent(this);
			children.add(newChild);
		}
	}

	/**
	 * Inserts a (key, value) pair to the manifest node.
	 * @param key
	 * @param value
	 */
	public void put(String key, String value) {
		ManifestParseTree keyNode = new ManifestParseTree(getLevel());

		Token keyToken = new Token(key, TokenType.LITERAL);
		keyNode.getTokens().add(keyToken);

		ManifestParseTree valueNode = new ManifestParseTree(getLevel());
		keyNode.getChildren().add(valueNode);
		valueNode.setParent(keyNode);

		Token valueToken = new Token(value, TokenType.LITERAL);
		valueNode.getTokens().add(valueToken);

		getChildren().add(keyNode);
		keyNode.setParent(this);
	}

	/**
	 * Inserts a (key, value) pair to the manifest node.
	 * @param key
	 * @param value
	 * @throws JSONException 
	 */
	public void put(String key, JSONObject object) throws JSONException {
		ManifestParseTree keyNode = new ManifestParseTree(getLevel());

		Token keyToken = new Token(key, TokenType.LITERAL);
		keyNode.getTokens().add(keyToken);

		for (String k : JSONObject.getNames(object)) {
			String v = object.getString(k);
			keyNode.put(k, v);
		}

		getChildren().add(keyNode);
		keyNode.setParent(this);
	}

	/**
	 * Updates the manifest node with the given value.
	 * @param key
	 */
	public void update(String value) {
		if (getChildren().isEmpty())
			return;

		ManifestParseTree child = getChildren().get(0);
		child.getTokens().clear();
		child.getTokens().add(new Token(value, TokenType.LITERAL));
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
	 * @return <code>true</code> if and only if the node represents a string property.
	 */
	public boolean isStringProperty() {
		if (getChildren().size() != 1)
			return false;

		if (isList())
			return false;

		ManifestParseTree valueNode = getChildren().get(0);
		if (valueNode.getChildren().size() != 0)
			return false;

		return true;
	}

	/**
	 * @return <code>true</code> if and only if the node represents a valid application memory property.
	 */
	public boolean isValidMemoryProperty() {
		if (!isStringProperty())
			return false;

		try {

			String memoryValue = getValue();
			Matcher matcher = memoryPattern.matcher(memoryValue);
			return matcher.matches();

		} catch (InvalidAccessException ex) {
			return false;
		}
	}

	/**
	 * @return <code>true</code> if and only if the node represents a valid non-negative valued property.
	 */
	public boolean isValidNonNegativeProperty() {
		if (!isStringProperty())
			return false;

		try {

			String instancesValue = getValue();
			Matcher matcher = nonNegativePattern.matcher(instancesValue);
			return matcher.matches();

		} catch (InvalidAccessException ex) {
			return false;
		}
	}

	/**
	 * Externalization helper method
	 */
	protected String toString(int indentation) {
		StringBuilder sb = new StringBuilder();

		if (getParent() == this) {

			/* special case: manifest root */
			sb.append("---").append(System.getProperty("line.separator")); //$NON-NLS-1$ //$NON-NLS-2$
			for (ManifestParseTree child : children)
				sb.append(child.toString(0)).append(System.getProperty("line.separator")); //$NON-NLS-1$

			return sb.toString();
		}

		/* print indentation */
		for (int i = 0; i < indentation; ++i)
			sb.append(" "); //$NON-NLS-1$

		/* print tokens */
		sb.append(getLabel());

		/* print mapping symbol if required */
		boolean isItemNode = isItemNode();
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

				if (!child.isItemNode() || getParent().isItemNode())
					sb.append(child.toString(indentation + 2));
				else
					sb.append(child.toString(indentation));
			}
		}

		return sb.toString();
	}

	/**
	 * Externalization to JSON format.
	 * @return JSON representation.
	 * @throws JSONException 
	 * @throws InvalidAccessException 
	 */
	public JSONObject toJSON() throws JSONException, InvalidAccessException {

		JSONObject rep = new JSONObject();
		for (ManifestParseTree child : getChildren())
			child.append(rep);

		return rep;
	}

	/**
	 * JSON externalization helper.
	 */
	protected void append(JSONObject rep) throws JSONException, InvalidAccessException {

		if (isList()) {
			JSONArray arr = new JSONArray();
			for (ManifestParseTree child : getChildren())
				child.append(arr);

			rep.put(getLabel(), arr);
			return;
		}

		if (getChildren().size() == 1 && getChildren().get(0).getChildren().size() == 0) {
			/* format: A: B (mapping) */
			rep.put(getLabel(), getValue());
			return;
		}

		JSONObject obj = new JSONObject();
		for (ManifestParseTree child : getChildren())
			child.append(obj);

		rep.put(getLabel(), obj);
	}

	/**
	 * JSON externalization helper.
	 */
	protected void append(JSONArray rep) throws JSONException, InvalidAccessException {

		if (getChildren().size() == 1 && getChildren().get(0).getChildren().size() == 0) {
			/* format: - A (note: no mapping) */
			rep.put(getChildren().get(0).getLabel());
			return;
		}

		/* otherwise, we expect an object */
		JSONObject obj = new JSONObject();
		for (ManifestParseTree child : getChildren())
			child.append(obj);

		rep.put(obj);
	}

	/**
	 * Persists the manifest YAML representation into the given file store.
	 * @param fileStore File store to persist the manifest. Note: if the given
	 * file exists, it's contents are going to be overridden.
	 * @throws CoreException
	 */
	public void persist(IFileStore fileStore) throws CoreException {

		PrintStream ps = null;
		try {

			String representation = toString();
			OutputStream out = fileStore.openOutputStream(EFS.OVERWRITE, null);
			ps = new PrintStream(out);
			ps.print(representation);

		} finally {
			if (ps != null)
				IOUtilities.safeClose(ps);
		}
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

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof ManifestParseTree))
			return false;

		ManifestParseTree tree = (ManifestParseTree) obj;
		return getLabel().equals(tree.getLabel());
	}

	@Override
	public int hashCode() {
		return getLabel().hashCode();
	}
}