/*******************************************************************************
 * Copyright (c) 2014 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Michael Ochmann <michael.ochmann@sap.com> - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.xfer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Buffering wrapper for an input stream that allows reading the stream line-by-line or byte-wise in any combination,
 * and counting the actual bytes read. This stream does not support mark/reset.
 */
public class ImportStream extends InputStream {

	private BufferedInputStream buffered;
	private long count;

	/** Creates an <code>ImportStream</code> wrapping the given input stream. */
	public ImportStream(InputStream in) {
		buffered = new BufferedInputStream(in);
	}

	/** Returns the number of bytes read from this stream. */
	public long count() {
		return count;
	}

	/** Resets the number of bytes read from this stream. */
	public void resetCount() {
		count = 0;
	}

	/**
	 * Reads a line of text until the next <tt>\r\n</tt> ot <t>\n</t> end of line delimiter. This method assumes that
	 * the input stream is ISO-8859-1 encoded, which is true for header part of an HTTP message.
	 * @return the line of text without the end of line delimiter, or <tt>"\r\n"</tt> if the line was empty.
	 * @throws IOException if an i/o error occured.
	 */
	public String readLine() throws IOException {
		StringBuilder sb = new StringBuilder();
		String line = null;
		boolean eol = false;
		while (!eol) {
			int c = read();
			switch (c) {
				case -1 :
				case '\n' :
					line = sb.length() > 0 ? sb.toString() : "\r\n"; //$NON-NLS-1$
					eol = true;
					break;
				case '\r' :
					break;
				default :
					sb.append((char) c);
			}
		}
		if (line == null) {
			throw new IOException("Unexpected EOF");
		}
		return line;
	}

	@Override
	public int read() throws IOException {
		int c = buffered.read();
		if (c != -1)
			++count;
		return c;
	}

	@Override
	public int read(byte b[]) throws IOException {
		int read = buffered.read(b);
		if (read != -1)
			count += read;
		return read;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int read = buffered.read(b, off, len);
		if (read != -1)
			count += read;
		return read;
	}

	@Override
	public long skip(long n) throws IOException {
		long read = buffered.skip(n);
		if (read != -1)
			count += read;
		return read;
	}

	public long skipAll() throws IOException {
		long read = 0;
		long skipped = 0;
		while ((skipped = skip(Long.MAX_VALUE)) > 0) {
			read += skipped;
			count += skipped;
		}
		return read;
	}

	@Override
	public int available() throws IOException {
		return buffered.available();
	}

	@Override
	public void close() throws IOException {
		buffered.close();
	}
}
