/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.xfer;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.zip.*;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.FrameworkUtil;

/**
 * Represents an import from client operation in progress.
 */
class ClientImport {

	private static final String FILE_DATA = "xfer.data"; //$NON-NLS-1$
	private static final String FILE_INDEX = "xfer.properties"; //$NON-NLS-1$
	private static final String KEY_FILE_NAME = "FileName"; //$NON-NLS-1$
	private static final String KEY_LENGTH = "Length"; //$NON-NLS-1$
	private static final String KEY_OPTIONS = "Options"; //$NON-NLS-1$
	private static final String KEY_PATH = "Path"; //$NON-NLS-1$
	private static final String KEY_TRANSFERRED = "Transferred"; //$NON-NLS-1$

	/**
	 * The UUID of this import operation.
	 */
	private final String id;

	private Properties props = new Properties();
	private final ServletResourceHandler<IStatus> statusHandler;

	/**
	 * Creates a new import. This may represent an import that has not yet started,
	 * or one that is already underway.
	 */
	ClientImport(String id, ServletResourceHandler<IStatus> servletResourceHandler) throws IOException {
		this.id = id;
		this.statusHandler = servletResourceHandler;
		restore();
	}

	/**
	 * Completes a move after a file transfer. Returns <code>true</code> if the move was
	 * successful, and <code>false</code> otherwise. In case of failure, this method
	 * handles setting an appropriate response.
	 */
	private boolean completeMove(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		IPath destPath = new Path(getPath()).append(getFileName());
		try {
			IFileStore source = EFS.getStore(new File(getStorageDirectory(), FILE_DATA).toURI());
			IFileStore destination = NewFileServlet.getFileStore(destPath);
			source.move(destination, EFS.OVERWRITE, null);
		} catch (CoreException e) {
			String msg = NLS.bind("Failed to complete file transfer on {0}", destPath.toString());
			statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return false;
		}
		return true;
	}

	/**
	 * We have just received the final chunk of data for a file upload.
	 * Complete the transfer by moving the uploaded content into the
	 * workspace.
	 * @throws IOException 
	 */
	private void completeTransfer(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		List<String> options = getOptions();
		boolean success;
		if (!options.contains("raw")) { //$NON-NLS-1$
			success = completeUnzip(req, resp);
		} else {
			success = completeMove(req, resp);
		}
		if (success) {
			resp.setHeader(ProtocolConstants.HEADER_LOCATION, "/file" + getPath()); //$NON-NLS-1$
			resp.setStatus(HttpServletResponse.SC_CREATED);
			resp.getOutputStream().write(new String("<head></head><body><textarea>{}</textarea></body>").getBytes());
		}
	}

	/**
	 * Unzips the transferred file. Returns <code>true</code> if the unzip was
	 * successful, and <code>false</code> otherwise. In case of failure, this method
	 * handles setting an appropriate response.
	 */
	private boolean completeUnzip(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		IPath destPath = new Path(getPath());
		try {
			ZipFile source = new ZipFile(new File(getStorageDirectory(), FILE_DATA));
			IFileStore destinationRoot = NewFileServlet.getFileStore(destPath);
			Enumeration<? extends ZipEntry> entries = source.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				IFileStore destination = destinationRoot.getChild(entry.getName());
				if (entry.isDirectory())
					destination.mkdir(EFS.NONE, null);
				else {
					destination.getParent().mkdir(EFS.NONE, null);
					IOUtilities.pipe(source.getInputStream(entry), destination.openOutputStream(EFS.NONE, null), false, true);
				}
			}
			source.close();
		} catch (ZipException e) {
			//zip exception implies client sent us invalid input
			String msg = NLS.bind("Failed to complete file transfer on {0}", destPath.toString());
			statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, e));
			return false;
		} catch (Exception e) {
			//other failures should be considered server errors
			String msg = NLS.bind("Failed to complete file transfer on {0}", destPath.toString());
			statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return false;
		}
		return true;
	}

	/**
	 * A post operation represents the beginning of an import operation. This method
	 * initializes the import and sets an appropriate response.
	 */
	void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		save();
		//if the transfer length header is not specified, then the file is being uploaded during the POST
		if (req.getHeader(ProtocolConstants.HEADER_XFER_LENGTH) == null) {
			doPut(req, resp);
			return;
		}
		//otherwise the POST is just starting a transfer to be completed later
		resp.setStatus(HttpServletResponse.SC_OK);
		setResponseLocationHeader(req, resp);
	}

	private void setResponseLocationHeader(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		URI requestURI = ServletResourceHandler.getURI(req);
		String responsePath = "/" + new Path(requestURI.getPath()).segment(0) + "/import/" + id; //$NON-NLS-1$ //$NON-NLS-2$
		URI responseURI;
		try {
			responseURI = new URI(requestURI.getScheme(), requestURI.getAuthority(), responsePath, null, null);
		} catch (URISyntaxException e) {
			//should not be possible
			throw new ServletException(e);
		}
		resp.setHeader(ProtocolConstants.HEADER_LOCATION, responseURI.toString());
	}

	/**
	 * A put is used to send a chunk of a file.
	 */
	void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		int transferred = getTransferred();
		int length = getLength();
		int headerLength = Integer.valueOf(req.getHeader(ProtocolConstants.HEADER_CONTENT_LENGTH));
		String rangeString = req.getHeader(ProtocolConstants.HEADER_CONTENT_RANGE);
		if (rangeString == null)
			rangeString = "bytes 0-" + (length - 1) + '/' + length;
		ContentRange range = ContentRange.parse(rangeString);
		if (length != range.getLength()) {
			fail(req, resp, "Chunk specifies an incorrect document length");
			return;
		}
		if (range.getStartByte() > transferred) {
			fail(req, resp, "Chunk missing; Expected start byte: " + transferred);
			return;
		}
		if (range.getEndByte() < range.getStartByte()) {
			fail(req, resp, "Invalid range: " + rangeString);
			return;
		}
		int chunkSize = 1 + range.getEndByte() - range.getStartByte();
		if (chunkSize != headerLength) {
			fail(req, resp, "Content-Range doesn't agree with Content-Length");
			return;
		}
		byte[] chunk = readChunk(req, chunkSize);
		FileOutputStream fout = null;
		try {
			fout = new FileOutputStream(new File(getStorageDirectory(), FILE_DATA), true);
			FileChannel channel = fout.getChannel();
			channel.position(range.getStartByte());
			channel.write(ByteBuffer.wrap(chunk));
			channel.close();
		} finally {
			try {
				if (fout != null)
					fout.close();
			} catch (IOException e) {
				//ignore secondary failure
			}
		}
		transferred = range.getEndByte() + 1;
		setTransferred(transferred);
		save();
		if (transferred >= length) {
			completeTransfer(req, resp);
			return;
		}
		resp.setStatus(308);//Resume Incomplete
		resp.setHeader("Range", "bytes 0-" + range.getEndByte());
		setResponseLocationHeader(req, resp);
	}

	/**
	 * Reads the chunk of data to be imported from the request's input stream.
	 */
	private byte[] readChunk(HttpServletRequest req, int chunkSize) throws IOException {
		ServletInputStream requestStream = req.getInputStream();
		String contentType = req.getHeader(ProtocolConstants.HEADER_CONTENT_TYPE);
		if (contentType.startsWith("multipart")) //$NON-NLS-1$
			return readMultiPartChunk(requestStream, contentType);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(chunkSize);
		IOUtilities.pipe(requestStream, outputStream, false, false);
		return outputStream.toByteArray();
	}

	private byte[] readMultiPartChunk(ServletInputStream requestStream, String contentType) throws IOException {
		//fast forward stream past multi-part header
		int boundaryOff = contentType.indexOf("boundary="); //$NON-NLS-1$
		String boundary = contentType.substring(boundaryOff + 9);
		BufferedReader reader = new BufferedReader(new InputStreamReader(requestStream, "ISO-8859-1")); //$NON-NLS-1$
		StringBuffer out = new StringBuffer();
		//skip headers up to the first blank line
		String line = reader.readLine();
		while (line != null && line.length() > 0)
			line = reader.readLine();
		//now process the file

		char[] buf = new char[1000];
		int read;
		while ((read = reader.read(buf)) > 0) {
			out.append(buf, 0, read);
		}
		//remove the boundary from the output (end of input is \r\n--<boundary>--\r\n)
		out.setLength(out.length() - (boundary.length() + 8));
		return out.toString().getBytes("ISO-8859-1"); //$NON-NLS-1$
	}

	private void fail(HttpServletRequest req, HttpServletResponse resp, String msg) throws ServletException {
		statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
	}

	private String getFileName() {
		return props.getProperty(KEY_FILE_NAME, ""); //$NON-NLS-1$
	}

	private int getLength() {
		return Integer.valueOf(props.getProperty(KEY_LENGTH, "0")); //$NON-NLS-1$
	}

	private List<String> getOptions() {
		return TransferServlet.getOptions(props.getProperty(KEY_OPTIONS, ""));//$NON-NLS-1$
	}

	private String getPath() {
		return props.getProperty(KEY_PATH, ""); //$NON-NLS-1$
	}

	private File getStorageDirectory() {
		return FrameworkUtil.getBundle(ClientImport.class).getDataFile("xfer/" + id); //$NON-NLS-1$
	}

	/**
	 * Returns the number of bytes transferred so far.
	 */
	private Integer getTransferred() {
		return Integer.valueOf(props.getProperty(KEY_TRANSFERRED, "0")); //$NON-NLS-1$
	}

	/**
	 * Load any progress information for the import so far.
	 */
	void restore() throws IOException {
		try {
			File dir = getStorageDirectory();
			File index = new File(dir, FILE_INDEX);
			props.load(new FileInputStream(index));
		} catch (FileNotFoundException e) {
			//ok if file doesn't exist yet
		}
	}

	void save() throws IOException {
		File dir = getStorageDirectory();
		dir.mkdirs();
		File index = new File(dir, FILE_INDEX);
		props.store(new FileOutputStream(index), null);
	}

	public void setFileName(String name) {
		props.put(KEY_FILE_NAME, name == null ? "" : name); //$NON-NLS-1$
	}

	/**
	 * Sets the total length of the file being imported.
	 */
	public void setLength(long length) {
		props.put(KEY_LENGTH, Long.toString(length));
	}

	public void setOptions(String options) {
		props.put(KEY_OPTIONS, options == null ? "" : options); //$NON-NLS-1$
	}

	/**
	 * Sets the path of the file in the workspace once the import completes.
	 */
	public void setPath(IPath path) {
		props.put(KEY_PATH, path.toString());
	}

	private void setTransferred(int transferred) {
		props.put(KEY_TRANSFERRED, Integer.toString(transferred));
	}

}
