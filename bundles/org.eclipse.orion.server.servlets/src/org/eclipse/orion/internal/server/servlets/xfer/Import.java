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
import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.FrameworkUtil;

/**
 * Represents an import operation in progress.
 */
class Import {

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
	Import(String id, ServletResourceHandler<IStatus> servletResourceHandler) throws IOException {
		this.id = id;
		this.statusHandler = servletResourceHandler;
		restore();
	}

	private void completeMove(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		IPath destPath = new Path(getPath()).append(getFileName());
		try {
			IFileStore source = EFS.getStore(new File(getStorageDirectory(), FILE_DATA).toURI());
			IFileStore destination = getFileStore(destPath, req.getRemoteUser());
			source.move(destination, EFS.OVERWRITE, null);
		} catch (CoreException e) {
			String msg = NLS.bind("Failed to complete file transfer on {0}", destPath.toString());
			statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return;
		}
	}

	/**
	 * We have just received the final chunk of data for a file upload.
	 * Complete the transfer by moving the uploaded content into the
	 * workspace.
	 */
	private void completeTransfer(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String options = getOptions();
		if (options.contains("unzip")) {
			completeUnzip(req, resp);
		} else {
			completeMove(req, resp);
		}
		resp.setStatus(HttpServletResponse.SC_CREATED);
	}

	private void completeUnzip(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		IPath destPath = new Path(getPath());
		try {
			ZipFile source = new ZipFile(new File(getStorageDirectory(), FILE_DATA));
			IFileStore destinationRoot = getFileStore(destPath, req.getRemoteUser());
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
		} catch (Exception e) {
			String msg = NLS.bind("Failed to complete file transfer on {0}", destPath.toString());
			statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return;
		}
	}

	/**
	 * A post operation represents the beginning of an import operation. This method
	 * initializes the import and sets an appropriate response.
	 */
	void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		save();
		resp.setStatus(HttpServletResponse.SC_OK);
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

	private void fail(HttpServletRequest req, HttpServletResponse resp, String msg) throws ServletException {
		statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
	}

	private String getFileName() {
		return props.getProperty(KEY_FILE_NAME, ""); //$NON-NLS-1$
	}

	/**
	 * Returns the store representing the file to be retrieved for the given
	 * request or <code>null</code> if an error occurred.
	 */
	protected IFileStore getFileStore(IPath path, String authority) {
		//first check if we have an alias registered
		if (path.segmentCount() > 0) {
			URI alias = Activator.getDefault().lookupAlias(path.segment(0));
			if (alias != null)
				try {
					return EFS.getStore(Util.getURIWithAuthority(alias, authority)).getFileStore(path.removeFirstSegments(1));
				} catch (CoreException e) {
					LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, 1, "An error occured when getting file store for path '" + path + "' and alias '" + alias + "'", e));
					// fallback is to try the same path relatively to the root
				}
		}
		//assume it is relative to the root
		URI rootStoreURI = Activator.getDefault().getRootLocationURI();
		try {
			return EFS.getStore(Util.getURIWithAuthority(rootStoreURI, authority)).getFileStore(path);
		} catch (CoreException e) {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SERVER_SERVLETS, 1, "An error occured when getting file store for path '" + path + "' and root '" + rootStoreURI + "'", e));
			// fallback and return null
		}

		return null;
	}

	private int getLength() {
		return Integer.valueOf(props.getProperty(KEY_LENGTH, "0")); //$NON-NLS-1$
	}

	private String getOptions() {
		return props.getProperty(KEY_OPTIONS, ""); //$NON-NLS-1$
	}

	private String getPath() {
		return props.getProperty(KEY_PATH, ""); //$NON-NLS-1$
	}

	private File getStorageDirectory() {
		return FrameworkUtil.getBundle(Import.class).getDataFile("xfer/" + id); //$NON-NLS-1$
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
			props.load(new FileReader(index));
		} catch (FileNotFoundException e) {
			//ok if file doesn't exist yet
		}
	}

	void save() throws IOException {
		File dir = getStorageDirectory();
		dir.mkdirs();
		File index = new File(dir, FILE_INDEX);
		props.store(new FileWriter(index), null);
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
		props.put(KEY_OPTIONS, options);
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
