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
import java.util.Properties;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.*;
import org.osgi.framework.FrameworkUtil;

/**
 * Represents an import operation in progress.
 */
class Import {

	private static final String FILE_DATA = "xfer.data"; //$NON-NLS-1$
	private static final String FILE_INDEX = "xfer.properties"; //$NON-NLS-1$
	private static final String KEY_FILE_NAME = "FileName"; //$NON-NLS-1$
	private static final String KEY_LENGTH = "Length"; //$NON-NLS-1$
	private static final String KEY_PATH = "Path"; //$NON-NLS-1$
	private static final String KEY_TRANSFERRED = "Transferred"; //$NON-NLS-1$

	/**
	 * The UUID of this import operation.
	 */
	private final String id;

	private Properties props = new Properties();
	private final ServletResourceHandler<IStatus> statusHandler;

	Import(String id, ServletResourceHandler<IStatus> servletResourceHandler) throws IOException {
		this.id = id;
		this.statusHandler = servletResourceHandler;
		restore();
	}

	/**
	 * A post operation represents the beginning of an import operation. This method
	 * initializes the import and sets an appropriate response.
	 */
	void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		save();
		resp.setStatus(HttpServletResponse.SC_OK);
		URI requestURI = ServletResourceHandler.getURI(req);
		String responsePath = "/" + new Path(requestURI.getPath()).segment(0) + "/import/" + id;
		URI responseURI;
		try {
			responseURI = new URI(requestURI.getScheme(), requestURI.getAuthority(), responsePath, null, null);
		} catch (URISyntaxException e) {
			//should not be possible
			throw new ServletException(e);
		}
		resp.setHeader("Location", responseURI.toString());
	}

	/**
	 * A put is used to send a chunk of a file.
	 */
	void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		int transferred = getTransferred();
		int length = getLength();
		int headerLength = Integer.valueOf(req.getHeader(ProtocolConstants.HEADER_CONTENT_LENGTH));
		String rangeString = req.getHeader(ProtocolConstants.HEADER_CONTENT_RANGE);
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
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(chunkSize);
		IOUtilities.pipe(req.getInputStream(), outputStream, false, true);
		byte[] chunk = outputStream.toByteArray();
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

	}

	/**
	 * We have just received the final chunk of data for a file upload.
	 * Complete the transfer by moving the uploaded content into the
	 * workspace.
	 */
	private void completeTransfer(HttpServletRequest req, HttpServletResponse resp) {
	}

	private void fail(HttpServletRequest req, HttpServletResponse resp, String msg) throws ServletException {
		statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
	}

	private int getLength() {
		return Integer.valueOf(props.getProperty(KEY_LENGTH, "0")); //$NON-NLS-1$
	}

	private File getStorageDirectory() {
		return FrameworkUtil.getBundle(Import.class).getDataFile("xfer/" + id);
	}

	/**
	 * Returns the number of bytes transferred so far.
	 */
	private Integer getTransferred() {
		return Integer.valueOf(props.getProperty(KEY_TRANSFERRED, "0")); //$NON-NLS-1$
	}

	void save() throws IOException {
		File dir = getStorageDirectory();
		dir.mkdirs();
		File index = new File(dir, FILE_INDEX);
		props.store(new FileWriter(index), null);
	}

	void restore() throws IOException {
		try {
			File dir = getStorageDirectory();
			File index = new File(dir, FILE_INDEX);
			props.load(new FileReader(index));
		} catch (FileNotFoundException e) {
			//ok if file doesn't exist yet
		}
	}

	public void setFileName(String name) {
		props.put(KEY_FILE_NAME, name);
	}

	/**
	 * Sets the total length of the file being imported.
	 */
	public void setLength(long length) {
		props.put(KEY_LENGTH, Long.toString(length));
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
