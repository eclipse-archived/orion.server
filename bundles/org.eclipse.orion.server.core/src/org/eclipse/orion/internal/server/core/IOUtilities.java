/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.core;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Various static helper methods for I/O processing.
 */
public class IOUtilities {
	public static void pipe(InputStream inputStream, OutputStream outputStream) throws IOException {
		pipe(inputStream, outputStream, false, false);
	}

	public static void pipe(InputStream inputStream, OutputStream outputStream, boolean closeIn, boolean closeOut) throws IOException {
		byte[] buffer = new byte[4096];
		int read = 0;
		try {
			while ((read = inputStream.read(buffer)) != -1)
				outputStream.write(buffer, 0, read);
		} finally {
			if (closeIn)
				safeClose(inputStream);
			if (closeOut)
				safeClose(outputStream);
		}
	}

	public static void pipe(Reader input, Writer output) throws IOException {
		pipe(input, output, false, false);
	}

	public static void pipe(Reader reader, Writer writer, boolean closeReader, boolean closeWriter) throws IOException {
		try {
			char[] buffer = new char[4096];
			int read = 0;
			while ((read = reader.read(buffer)) != -1)
				writer.write(buffer, 0, read);
		} finally {
			if (closeReader)
				safeClose(reader);
			if (closeWriter)
				safeClose(writer);
		}
	}

	/**
	 * Returns the value of a request parameter as a String, or null if 
	 * the parameter does not exist in the query string.
	 * 
	 * Method is similar to HttpServletRequest.getParameter(String name) method, but it does not
	 * interfere with HttpServletRequest.getInputStream() and HttpServletRequest.getReader().  
	 * @param request a request object
	 * @param name a String specifying the name of the parameter
	 * @return a String representing the single value of the parameter
	 */
	public static String getQueryParameter(HttpServletRequest request, String name) {
		String queryString = request.getQueryString();
		if (queryString == null)
			return null;

		for (String paramString : queryString.split("&")) { //$NON-NLS-1$
			if (paramString.startsWith(name)) {
				String[] nameAndValue = paramString.split("=", 2); //$NON-NLS-1$
				if (nameAndValue.length == 2)
					return nameAndValue[1];
				return ""; // parameter has no value //$NON-NLS-1$
			}
		}
		// parameter not found
		return null;
	}

	/**
	 * Closes a stream or reader and ignores any resulting exception. This is useful
	 * when doing cleanup in a finally block where secondary exceptions
	 * are not worth logging.
	 */
	public static void safeClose(Closeable closeable) {
		try {
			if (closeable != null)
				closeable.close();
		} catch (IOException e) {
			//ignore
		}
	}

	public static String toString(InputStream is) throws IOException {
		if (is == null)
			return ""; //$NON-NLS-1$
		StringWriter writer = new StringWriter();
		pipe(new InputStreamReader(is, "UTF-8"), writer, true, false); //$NON-NLS-1$
		return writer.toString();
	}

	public static InputStream toInputStream(String s) throws UnsupportedEncodingException {
		return new ByteArrayInputStream(s.getBytes("UTF-8")); //$NON-NLS-1$^M
	}

	public static Map<String, String> parseMultiPart(final InputStream requestStream, final String boundary) throws IOException {
		String string = IOUtilities.toString(requestStream);
		System.out.println(string);
		BufferedReader reader = new BufferedReader(new StringReader(string));
		StringBuilder buf = new StringBuilder();
		Map<String, String> parts = new HashMap<String, String>();
		String name = null;
		String prev = "";
		boolean end = false;	//flag which tells that there is no more content left to read (but the one already read should be parsed)
		try {
			String[] line;
			while (!end) {
				line = getLine(reader);
				if ("".equals(line[1])) //$NON-NLS-1$
					end = true;
				if (line[0].equals("--" + boundary)) { //$NON-NLS-1$
					if (buf.length() > 0) {
						parts.put(name, buf.toString());
						buf.setLength(0);
					}
					line = getLine(reader); // Content-Disposition: form-data; name="{name}"...
					int i = line[0].indexOf("name=\""); //$NON-NLS-1$
					String s = line[0].substring(i + "name=\"".length()); //$NON-NLS-1$
					name = s.substring(0, s.indexOf('"'));
					getLine(reader); // an empty line
					if (name.equals("uploadedfile")) { //$NON-NLS-1$
						getLine(reader); // "Content-Type: application/octet-stream"
					}
				} else if (line[0].equals("--" + boundary + "--")) { //$NON-NLS-1$ //$NON-NLS-2$
					parts.put(name, buf.toString());
				} else {
					if (buf.length() > 0)
						buf.append(prev);
					buf.append(line[0]);
					prev = line[1];
				}
			}
		} finally {
			IOUtilities.safeClose(reader);
		}
		return parts;
	}

	public static String[] getLine(BufferedReader reader) throws IOException {
		StringBuilder lineBuilder = new StringBuilder();
		String lineDelimiter = "";
		String line = "";
		char first = '0';
		while (true) {
			first = (char) reader.read();
			if (first == (char) -1) {
				line = lineBuilder.toString();
				lineDelimiter = "";
				return new String[] {line, lineDelimiter};
			} else if (first == '\n') {
				line = lineBuilder.toString();
				lineDelimiter = "\n";
				return new String[] {line, lineDelimiter};
			} else if (first == '\r') {
				char second = (char) reader.read();
				if (second == (char) -1) {
					lineBuilder.append(first);
					line = lineBuilder.toString();
					lineDelimiter = "";
					return new String[] {line, lineDelimiter};
				};
				if (second == '\n') {
					line = lineBuilder.toString();
					lineDelimiter = "\r\n";
					return new String[] {line, lineDelimiter};
				} else {
					lineBuilder.append(first);
					lineBuilder.append(second);
				}

			} else {
				lineBuilder.append(first);
			}
		}
	}
}