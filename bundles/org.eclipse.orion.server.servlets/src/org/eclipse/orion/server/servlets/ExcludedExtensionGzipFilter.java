/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.servlets;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.zip.GZIPOutputStream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * A filter that gzips all contents except excluded extensions and server-side includes.
 */
public class ExcludedExtensionGzipFilter implements Filter {
	public static class ServletOutputStreamWrapper extends ServletOutputStream {
		private OutputStream _outputStream;

		public ServletOutputStreamWrapper(OutputStream outputStream) throws IOException {
			super();
			_outputStream = outputStream;
		}

		public void write(int b) throws IOException {
			_outputStream.write(b);
		}

		public void write(byte[] b) throws IOException {
			_outputStream.write(b);
		}

		public void write(byte[] b, int off, int len) throws IOException {
			_outputStream.write(b, off, len);
		}

		public void flush() throws IOException {
			_outputStream.flush();
		}

		public void close() throws IOException {
			_outputStream.close();
		}
	}

	public static class GZipServletResponse extends HttpServletResponseWrapper {

		private PrintWriter _printWriter;
		private ServletOutputStream _servletOutputStream;

		public GZipServletResponse(HttpServletResponse response) {
			super(response);
		}

		@Override
		public ServletOutputStream getOutputStream() throws IOException {
			if (_printWriter != null) {
				throw new IllegalStateException();
			}
			if (_servletOutputStream == null) {
				((HttpServletResponse) getResponse()).setHeader("Content-Encoding", "gzip");
				_servletOutputStream = new ServletOutputStreamWrapper(new GZIPOutputStream(getResponse().getOutputStream()));
			}
			return _servletOutputStream;
		}

		@Override
		public PrintWriter getWriter() throws IOException {
			if (_printWriter == null) {
				if (_servletOutputStream != null) {
					throw new IllegalStateException();
				}
				HttpServletResponse response = (HttpServletResponse) getResponse();
				response.setHeader("Content-Encoding", "gzip");
				_servletOutputStream = new ServletOutputStreamWrapper(new GZIPOutputStream(response.getOutputStream()));
				_printWriter = new PrintWriter(new OutputStreamWriter(_servletOutputStream, response.getCharacterEncoding()));
			}
			return _printWriter;
		}

		@Override
		public void flushBuffer() throws IOException {
			if (_printWriter != null) {
				_printWriter.flush();
			} else if (_servletOutputStream != null) {
				_servletOutputStream.flush();
			}
		}

		@Override
		public void setContentLength(int len) {
			// do not set this header
		}

		public void close() throws IOException {
			if (_printWriter != null) {
				_printWriter.close();
			} else if (_servletOutputStream != null) {
				_servletOutputStream.close();
			}
		}
	}

	static final String INCLUDE_REQUEST_URI_ATTRIBUTE = "javax.servlet.include.request_uri"; //$NON-NLS-1$
	static final String FORWARD_REQUEST_URI_ATTRIBUTE = "javax.servlet.forward.request_uri"; //$NON-NLS-1$

	private HashSet<String> _excludedExtensions = new HashSet<String>();

	public void init(FilterConfig filterConfig) throws ServletException {
		String excludedExtensionsParam = filterConfig.getInitParameter("excludedExtensions");
		if (excludedExtensionsParam != null) {
			StringTokenizer tokenizer = new StringTokenizer(excludedExtensionsParam, ",", false);
			while (tokenizer.hasMoreTokens()) {
				_excludedExtensions.add(tokenizer.nextToken().trim());
			}
		}
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (isApplicable((HttpServletRequest) request)) {
			GZipServletResponse gzipResponse = new GZipServletResponse((HttpServletResponse) response);
			chain.doFilter(request, gzipResponse);
			gzipResponse.close();
		} else {
			chain.doFilter(request, response);
		}
	}

	private boolean isApplicable(HttpServletRequest req) {
		if (req.getAttribute(INCLUDE_REQUEST_URI_ATTRIBUTE) != null || req.getAttribute(FORWARD_REQUEST_URI_ATTRIBUTE) != null) {
			return false;
		}

		String acceptEncoding = req.getHeader("Accept-Encoding");
		if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
			return false;
		}

		String pathInfo = req.getPathInfo();
		if (pathInfo == null || _excludedExtensions.isEmpty()) {
			return true;
		}

		int dot = pathInfo.lastIndexOf('.');
		if (dot != -1) {
			String extension = pathInfo.substring(dot + 1).toLowerCase();
			if (_excludedExtensions.contains(extension)) {
				return false;
			}
		}
		return true;
	}

	public void destroy() {
	}
}
