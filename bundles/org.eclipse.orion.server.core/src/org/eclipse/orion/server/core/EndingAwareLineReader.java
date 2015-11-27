/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

public class EndingAwareLineReader extends BufferedReader {

	private LineDelimiter lineDelimiter = null;
	private String line = null;
	private StringBuilder lineBuilder = new StringBuilder();

	public EndingAwareLineReader(Reader in) {
		super(in);
	}

	/**
	 * Returns next line from reader and sets lineDelimiter to proper line ending
	 * @return String containing the next line
	 * @throws IOException
	 */
	@Override
	public String readLine() throws IOException {
		char first = '0';
		while (true) {
			first = (char) read();
			if (first == (char) -1) {
				if (lineBuilder.length() == 0) return null;
				line = lineBuilder.toString();
				lineDelimiter = LineDelimiter.EMPTY;
				lineBuilder.setLength(0);
				return line;
			} else if (first == '\n') {
				line = lineBuilder.toString();
				lineDelimiter = LineDelimiter.LF;
				lineBuilder.setLength(0);
				return line;
			} else if (first == '\r') {
				char second = (char) read();
				if (second == (char) -1) {
					line = lineBuilder.toString();
					lineDelimiter = LineDelimiter.CR;
					lineBuilder.setLength(0);
					return line;
				};
				if (second == '\n') {
					line = lineBuilder.toString();
					lineDelimiter = LineDelimiter.CRLF;
					lineBuilder.setLength(0);
					return line;
				} else {
					line = lineBuilder.toString();
					lineDelimiter = LineDelimiter.CR;
					lineBuilder.setLength(0);
					lineBuilder.append(second);
					return line;
				}

			} else {
				lineBuilder.append(first);
			}
		}
	}

	/**
	 * Returns line delimiter associated with current line
	 * @return String containing line delimiter (\r, \r\n, \n)
	 */
	public LineDelimiter getLineDelimiter() {
		return lineDelimiter;
	}
	
	public static enum LineDelimiter {
		LF("\n"),CR("\r"),CRLF("\r\n"),EMPTY("");
		
		private final String lineDelimiter; 
		
		LineDelimiter(String ld) {
			this.lineDelimiter = ld;
		}
		
		@Override
		public String toString() {
			return lineDelimiter;
		}
	}
}
