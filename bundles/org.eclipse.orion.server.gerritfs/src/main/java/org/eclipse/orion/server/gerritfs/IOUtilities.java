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
package org.eclipse.orion.server.gerritfs;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

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
}