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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import org.eclipse.orion.server.cf.manifest.v2.*;

public class ManifestTokenizer implements Tokenizer {

	/* supported tokens */
	private static TokenPattern indentationPattern = new TokenPattern(" {1,}", TokenType.INDENTATION); //$NON-NLS-1$
	private static TokenPattern[] tokenPatterns = {new TokenPattern("- ", TokenType.ITEM_CONSTANT), //$NON-NLS-1$
			new TokenPattern("(: +)|(:$)", TokenType.MAPPING_CONSTANT), //$NON-NLS-1$
			new TokenPattern("\\$\\{[^ \\t\\n\\x0b\\r\\f\\$\\{\\}]+\\} *", TokenType.SYMBOL), //$NON-NLS-1$
			new TokenPattern( //

					/* quotation marks around a string literal (note, around equals no " sign allowed inside */
					"(\"[^\\t\\n\\x0b\\r\\f\\$\\{\\}\"]*\" *)|" + //$NON-NLS-1$

							/* special string literals with : inside, e.g. URLs */
							"([^ \\t\\n\\x0b\\r\\f\\$\\{\\}:][^ \\t\\n\\x0b\\r\\f\\$\\{\\}]*[^ \\t\\n\\x0b\\r\\f\\$\\{\\}:] *)" + //$NON-NLS-1$

							/* other, unquoted string literals */
							"|([^ \\t\\n\\x0b\\r\\f\\$\\{\\}] *)|(\\$[a-zA-Z0-9]+ *)", TokenType.LITERAL), //$NON-NLS-1$
	};

	private List<InputLine> input;
	private InputLine currentLine;
	private int currentIndentation;

	public ManifestTokenizer(List<InputLine> input) {
		this.input = input;
		currentIndentation = 0;
	}

	@Override
	public boolean hasNext() {
		if (input.isEmpty() && currentLine == null)
			return false;

		return true;
	}

	@Override
	public Token getNextToken() throws TokenizerException {
		if (input.isEmpty() && currentLine == null)
			throw new NoSuchElementException();

		Token token = null;
		if (currentLine == null) {
			currentLine = input.remove(0);
			currentIndentation = 0;

			/* special case: indentation */
			Matcher matcher = indentationPattern.getPattern().matcher(currentLine.getContent());
			if (matcher.find()) {
				String tokenValue = currentLine.getContent().substring(matcher.start(), matcher.end());
				currentIndentation = tokenValue.length();

				currentLine.setContent(matcher.replaceFirst("")); //$NON-NLS-1$
				if (currentLine.getContent().isEmpty())
					currentLine = null;

				return getNextToken();
			}
		}

		for (TokenPattern tokenPattern : tokenPatterns) {
			Matcher matcher = tokenPattern.getPattern().matcher(currentLine.getContent());
			if (matcher.find()) {

				String tokenValue = currentLine.getContent().substring(matcher.start(), matcher.end());
				token = new Token(tokenValue.trim(), tokenPattern.getType());
				token.setLineNumber(currentLine.getLineNumber());
				token.setIndentation(currentIndentation);

				currentLine.setContent(matcher.replaceFirst("")); //$NON-NLS-1$

				/* adjust list item indentation */
				if (TokenType.ITEM_CONSTANT == tokenPattern.getType())
					currentIndentation += 2;

				break;
			}
		}

		if (token == null)
			throw new TokenizerException(ManifestConstants.UNEXPECTED_INPUT_ERROR, currentLine);

		if (currentLine.getContent().isEmpty())
			currentLine = null;

		return token;
	}
}
