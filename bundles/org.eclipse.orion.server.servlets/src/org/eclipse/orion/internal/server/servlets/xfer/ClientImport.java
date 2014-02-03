/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.xfer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
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
	private static final String KEY_SOURCE_URL = "SourceURL"; //$NON-NLS-1$

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
			IFileStore destination = NewFileServlet.getFileStore(req, destPath);
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
			resp.setHeader(ProtocolConstants.HEADER_LOCATION, req.getContextPath() + "/file" + getPath()); //$NON-NLS-1$
			resp.setStatus(HttpServletResponse.SC_CREATED);
			resp.setContentType(ProtocolConstants.CONTENT_TYPE_HTML);
		}
	}

	private static boolean hasExcludedParent(IFileStore destination, IFileStore destinationRoot, List<String> excludedFiles) {
		if (destination.equals(destinationRoot)) {
			return false;
		}
		if (excludedFiles.contains(destination.getName())) {
			return true;
		}
		return hasExcludedParent(destination.getParent(), destinationRoot, excludedFiles);
	}

	/**
	 * Unzips the transferred file. Returns <code>true</code> if the unzip was
	 * successful, and <code>false</code> otherwise. In case of failure, this method
	 * handles setting an appropriate response.
	 */
	private boolean completeUnzip(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		IPath destPath = new Path(getPath());
		boolean force = false;
		List<String> filesFailed = new ArrayList<String>();
		if (req.getParameter("force") != null) {
			force = req.getParameter("force").equals("true");
		}
		List<String> excludedFiles = new ArrayList<String>();
		if (req.getParameter(ProtocolConstants.PARAM_EXCLUDE) != null) {
			excludedFiles = Arrays.asList(req.getParameter(ProtocolConstants.PARAM_EXCLUDE).split(","));
		}
		try {
			ZipFile source = new ZipFile(new File(getStorageDirectory(), FILE_DATA));
			IFileStore destinationRoot = NewFileServlet.getFileStore(req, destPath);
			Enumeration<? extends ZipEntry> entries = source.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				IFileStore destination = destinationRoot.getChild(entry.getName());
				if (!destinationRoot.isParentOf(destination) || hasExcludedParent(destination, destinationRoot, excludedFiles)) {
					//file should not be imported
					continue;
				}
				if (entry.isDirectory())
					destination.mkdir(EFS.NONE, null);
				else {
					if (!force && destination.fetchInfo().exists()) {
						filesFailed.add(entry.getName());
						continue;
					}
					destination.getParent().mkdir(EFS.NONE, null);
					// this filter will throw an IOException if a zip entry is larger than 100MB
					FilterInputStream maxBytesReadInputStream = new FilterInputStream(source.getInputStream(entry)) {
						private static final int maxBytes = 0x6400000; // 100MB
						private int totalBytes;

						private void addByteCount(int count) throws IOException {
							totalBytes += count;
							if (totalBytes > maxBytes) {
								throw new IOException("Zip file entry too large");
							}
						}

						@Override
						public int read() throws IOException {
							int c = super.read();
							if (c != -1) {
								addByteCount(1);
							}
							return c;
						}

						@Override
						public int read(byte[] b, int off, int len) throws IOException {
							int read = super.read(b, off, len);
							if (read != -1) {
								addByteCount(read);
							}
							return read;
						}
					};
					boolean fileWritten = false;
					try {
						IOUtilities.pipe(maxBytesReadInputStream, destination.openOutputStream(EFS.NONE, null), false, true);
						fileWritten = true;
					} finally {
						if (!fileWritten) {
							try {
								destination.delete(EFS.NONE, null);
							} catch (CoreException ce) {
								// best effort
							}
						}
					}
				}
			}
			source.close();

			if (!filesFailed.isEmpty()) {
				String failedFilesList = "";
				for (String file : filesFailed) {
					if (failedFilesList.length() > 0) {
						failedFilesList += ", ";
					}
					failedFilesList += file;
				}
				String msg = NLS.bind("Failed to transfer all files to {0}, the following files could not be overwritten {1}", destPath.toString(), failedFilesList);
				JSONObject jsonData = new JSONObject();
				jsonData.put("ExistingFiles", filesFailed);
				statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, jsonData, null));
				return false;
			}

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
		//if a source URl is specified then we are importing from remote URL
		if (getSourceURL() != null) {
			doImportFromURL(req, resp);
			return;
		}
		//if the transfer length header is not specified, then the file is being uploaded during the POST
		if (req.getHeader(ProtocolConstants.HEADER_XFER_LENGTH) == null) {
			doPut(req, resp);
			return;
		}
		//otherwise the POST is just starting a transfer to be completed later
		resp.setStatus(HttpServletResponse.SC_OK);
		setResponseLocationHeader(req, resp);
	}

	private void doImportFromURL(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		URL source = getSourceURL();
		IOUtilities.pipe(source.openStream(), new FileOutputStream(new File(getStorageDirectory(), FILE_DATA), true), true, true);
		completeTransfer(req, resp);
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
		resp.setHeader(ProtocolConstants.HEADER_LOCATION, ServletResourceHandler.resovleOrionURI(req, responseURI).toString());
	}

	/**
	 * A put is used to send a chunk of a file.
	 */
	void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		int transferred = getTransferred();
		int length = getLength();
		ContentRange range;
		int chunkSize = 0;
		if (length == 0) {
			range = ContentRange.parse("bytes 0-0/0");
		} else {
			String rangeString = req.getHeader(ProtocolConstants.HEADER_CONTENT_RANGE);
			if (rangeString == null)
				rangeString = "bytes 0-" + (length - 1) + '/' + length; //$NON-NLS-1$
			range = ContentRange.parse(rangeString);
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
			chunkSize = 1 + range.getEndByte() - range.getStartByte();
		}

		long piped = 0;
		String contentType = req.getHeader(ProtocolConstants.HEADER_CONTENT_TYPE);
		if (contentType != null && contentType.startsWith("multipart")) { //$NON-NLS-1$
			int boundaryOff = contentType.indexOf("boundary="); //$NON-NLS-1$
			if (boundaryOff < 0) {
				fail(req, resp, "Boundary parameter missing in Content-Type: " + contentType);
				return;
			}
			String boundary = contentType.substring(boundaryOff + 9);
			ImportStream in = new ImportStream(req.getInputStream());
			piped = pipeMultiPartChunk(in, range.getStartByte(), chunkSize, boundary);
		} else {
			ImportStream in = new ImportStream(req.getInputStream());
			piped = pipeChunk(in, range.getStartByte(), chunkSize, true);
		}
		if (chunkSize != piped) {
			fail(req, resp, "Content-Range doesn't agree with actual content length");
			return;
		}

		transferred = range.getEndByte() + 1;
		setTransferred(transferred);
		save();
		if (transferred >= length) {
			completeTransfer(req, resp);
			return;
		}
		resp.setStatus(308);//Resume Incomplete
		resp.setHeader("Range", "bytes 0-" + range.getEndByte()); //$NON-NLS-1$ //$NON-NLS-2$
		setResponseLocationHeader(req, resp);
	}

	private long pipeMultiPartChunk(ImportStream in, long position, int count, String boundary) throws IOException {
		try {
			// skip the preamble, i.e. skip lines until we find the starting delimiter
			String delimiter = "--" + boundary; //$NON-NLS-1$
			String line = in.readLine();
			while (!delimiter.equals(line))
				line = in.readLine();

			// skip part headers up to the first empty line
			while (!"\r\n".equals(line)) //$NON-NLS-1$
				line = in.readLine();

			// pipe the chunk
			long piped = pipeChunk(in, position, count, false);

			// if next comes the delimiter, the chunk was successfully piped:
			// return the number of piped bytes, which should be equal to count
			line = in.readLine();
			if ("\r\n".equals(line)) { //$NON-NLS-1$
				line = in.readLine();
				if (line.startsWith(delimiter)) {
					return piped;
				}
			}
		} finally {
			in.skipAll();
		}
		// if the chunk was not correctly delimited or was larger than the given byte range,
		// the actual number of bytes read from the stream is returned
		return in.count();
	}

	private long pipeChunk(ImportStream in, long position, int count, boolean skipIn) throws IOException {
		FileOutputStream fout = null;
		FileChannel channel = null;
		try {
			in.resetCount();
			fout = new FileOutputStream(new File(getStorageDirectory(), FILE_DATA), true);
			channel = fout.getChannel();
			channel.transferFrom(Channels.newChannel(in), position, count);
			if (skipIn) {
				in.skipAll();
			}
		} finally {
			IOUtilities.safeClose(channel);
			IOUtilities.safeClose(fout);
		}
		return in.count();
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

	private URL getSourceURL() {
		String urlString = props.getProperty(KEY_SOURCE_URL, null);
		try {
			return urlString == null ? null : new URL(urlString);
		} catch (MalformedURLException e) {
			//impossible because we validated URL in the setter method
			throw new RuntimeException(e);
		}
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

	/**
	 * Sets the URL of the file to be imported (optional).
	 */
	public void setSourceURL(String urlString) throws MalformedURLException {
		//ensure it is a valid absolute URL
		if (new URL(urlString).getProtocol() == null)
			throw new MalformedURLException(NLS.bind("Expected an absolute URI: {0}", urlString));
		props.put(KEY_SOURCE_URL, urlString);
	}

}
