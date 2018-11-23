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
package org.eclipse.orion.server.cf.manifest.v2.utils;

import org.eclipse.orion.server.cf.manifest.v2.*;

public class ManifestParser implements Parser {

	private ManifestParseTree root;
	private ManifestParseTree currentParent;

	private Token getNextToken(Tokenizer tokenizer) throws TokenizerException {
		return tokenizer.hasNext() ? tokenizer.getNextToken() : null;
	}

	@Override
	public ManifestParseTree parse(Tokenizer tokenizer) throws TokenizerException, ParserException {

		root = new ManifestParseTree();
		currentParent = root;

		ManifestParseTree node = null;
		Token currentToken = getNextToken(tokenizer);
		Token nextToken = null;

		while (true) {
			/* check if nothing more to do */
			if (currentToken == null)
				break;

			switch (currentToken.getType()) {
				case TokenType.MAPPING_CONSTANT :

					nextToken = getNextToken(tokenizer);
					if (nextToken != null && TokenType.MAPPING_CONSTANT == nextToken.getType())
						/* we're in a wrong state, admit failure */
						throw new ParserException(ManifestConstants.DUPLICATE_MAPPING_TOKEN, nextToken);

					if (nextToken != null && nextToken.getLineNumber() == currentToken.getLineNumber()) {

						/* there are token mappings */
						node = new ManifestParseTree(currentToken.getIndentation());
						node.getTokens().add(nextToken);

						currentParent.getChildren().add(node);
						node.setParent(currentParent);

						while ((nextToken = getNextToken(tokenizer)) != null && nextToken.getLineNumber() == currentToken.getLineNumber())
							node.getTokens().add(nextToken);
					}

					currentToken = nextToken;
					break;

				case TokenType.ITEM_CONSTANT :

					node = new ManifestParseTree(currentToken.getIndentation());
					node.getTokens().add(currentToken);

					/* attach to father node */
					setItemFather(node.getLevel());

					/* parent cannot have mapping values */
					for (ManifestParseTree child : currentParent.getChildren())
						if (!child.isItemNode())
							/* we're in a wrong state, admit failure */
							throw new ParserException(ManifestConstants.ILLEGAL_ITEM_TOKEN_MIX, currentToken);

					currentParent.getChildren().add(node);
					node.setParent(currentParent);

					/* look ahead */
					nextToken = getNextToken(tokenizer);
					if (nextToken != null && TokenType.LITERAL != nextToken.getType())
						/* we're in a wrong state, admit failure */
						throw new ParserException(ManifestConstants.ILLEGAL_ITEM_TOKEN, nextToken);

					currentParent = node;
					currentToken = nextToken;
					break;

				case TokenType.LITERAL :
				case TokenType.SYMBOL :

					node = new ManifestParseTree(currentToken.getIndentation());
					node.getTokens().add(currentToken);

					/* find father node */
					setFather(node.getLevel());

					/* parent cannot have item values */
					for (ManifestParseTree child : currentParent.getChildren())
						if (child.isItemNode())
							/* we're in a wrong state, admit failure */
							throw new ParserException(ManifestConstants.ILLEGAL_MAPPING_TOKEN_MIX, currentToken);

					currentParent.getChildren().add(node);
					node.setParent(currentParent);

					/* look ahead */
					nextToken = getNextToken(tokenizer);
					if (nextToken != null && TokenType.MAPPING_CONSTANT != nextToken.getType()) {

						/* accept empty mappings for list items */
						if (!currentParent.isItemNode())
							throw new ParserException(ManifestConstants.ILLEGAL_MAPPING_TOKEN, nextToken);

						if (nextToken != null && nextToken.getLineNumber() == currentToken.getLineNumber()) {

							/* there are token mappings */
							node.getTokens().add(nextToken);

							while ((nextToken = getNextToken(tokenizer)) != null && nextToken.getLineNumber() == currentToken.getLineNumber())
								node.getTokens().add(nextToken);
						}
					}

					currentParent = node;
					currentToken = nextToken;
					break;

				default :
					throw new ParserException(ManifestConstants.UNSUPPORTED_TOKEN_ERROR, currentToken);
			}
		}

		return root;
	}

	private void setFather(int currentLevel) {
		while (currentParent != root && currentParent.getLevel() >= currentLevel)
			currentParent = currentParent.getParent();
	}

	private void setItemFather(int currentLevel) {
		if (currentParent == root)
			return;

		if (currentParent.getLevel() > currentLevel) {
			currentParent = currentParent.getParent();
			setItemFather(currentLevel);
			return;
		}

		if (currentParent.isItemNode()) {
			currentParent = currentParent.getParent();
			setItemFather(currentLevel);
			return;
		}
	}
}
