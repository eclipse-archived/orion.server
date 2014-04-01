package org.eclipse.orion.internal.server.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

public class EndingAwareLineReader extends BufferedReader {

	private String lineDelimiter = null;
	private String line = null;
	private StringBuilder lineBuilder = new StringBuilder();

	public EndingAwareLineReader(Reader in) {
		super(in);
	}

	/**
	 * Determines whether reader reached the end of file
	 * @return
	 */
	public boolean hasNext() {
		return lineDelimiter == null || !("".equals(lineDelimiter));
	}

	/**
	 * Returns next line from reader and sets lineDelimiter to proper line ending
	 * @return String containing the next line
	 * @throws IOException
	 */
	public String getLine() throws IOException {
		char first = '0';
		while (true) {
			first = (char) read();
			if (first == (char) -1) {
				line = lineBuilder.toString();
				lineDelimiter = "";
				lineBuilder.setLength(0);
				return line;
			} else if (first == '\n') {
				line = lineBuilder.toString();
				lineDelimiter = "\n";
				lineBuilder.setLength(0);
				return line;
			} else if (first == '\r') {
				char second = (char) read();
				if (second == (char) -1) {
					line = lineBuilder.toString();
					lineDelimiter = "\r";
					lineBuilder.setLength(0);
					return line;
				};
				if (second == '\n') {
					line = lineBuilder.toString();
					lineDelimiter = "\r\n";
					lineBuilder.setLength(0);
					return line;
				} else {
					line = lineBuilder.toString();
					lineDelimiter = "\r";
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
	public String getLineDelimiter() {
		return lineDelimiter;
	}
}
