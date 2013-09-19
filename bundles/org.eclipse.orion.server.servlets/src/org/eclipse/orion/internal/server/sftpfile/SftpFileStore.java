/*******************************************************************************
 * Copyright (c) 2012, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.sftpfile;

import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.filesystem.provider.FileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.osgi.util.NLS;

/**
 * A handle representing a single file or directory in a remote SFTP server. 
 * This implementation defaults to using the user home directory when no
 * initial path is specified.
 */
public class SftpFileStore extends FileStore {

	/**
	 * Path representing user home directory.
	 */
	private static final Path HOME = new Path("~"); //$NON-NLS-1$

	private final URI host;
	private final IPath path;

	//cache fetched info just for purpose of determining if we are a directory
	private IFileInfo cachedInfo;

	/**
	 * Converts a jsch attributes object to an EFS file info.
	 */
	private static FileInfo attrsToInfo(String fileName, SftpATTRS stat) {
		FileInfo info = new FileInfo(fileName);
		info.setExists(true);
		info.setDirectory(stat.isDir());
		info.setLength(stat.getSize());
		info.setLastModified(((long) stat.getMTime()) * 1000);
		return info;
	}

	public SftpFileStore(URI host, IPath path) {
		this.host = host;
		//if no path specified, default to user home directory
		this.path = path.segmentCount() == 0 ? HOME : path;
	}

	@Override
	public IFileInfo[] childInfos(int options, IProgressMonitor monitor) throws CoreException {
		SynchronizedChannel channel = getChannel();
		try {
			Vector<LsEntry> children = channel.ls(getPathString(channel));
			List<IFileInfo> childInfos = new ArrayList<IFileInfo>(children.size());
			for (LsEntry child : children) {
				if (!shouldSkip(child.getFilename()))
					childInfos.add(attrsToInfo(child.getFilename(), child.getAttrs()));
			}
			return childInfos.toArray(new IFileInfo[childInfos.size()]);

		} catch (Exception e) {
			ChannelCache.flush(host);
			throw wrap(e);
		}
	}

	private String getPathString(SynchronizedChannel channel) throws SftpException {
		if (path.segmentCount() > 0 && path.segment(0).equals(HOME.segment(0))) {
			IPath result = new Path(channel.getHome());
			result = result.append(path.removeFirstSegments(1));
			return result.toString();
		}
		return path.toString();
	}

	@Override
	public String[] childNames(int options, IProgressMonitor monitor) throws CoreException {
		SynchronizedChannel channel = getChannel();
		try {
			Vector<LsEntry> children = channel.ls(getPathString(channel));
			List<String> childNames = new ArrayList<String>(children.size());
			for (LsEntry child : children) {
				if (!shouldSkip(child.getFilename()))
					childNames.add(child.getFilename());
			}
			return childNames.toArray(new String[childNames.size()]);

		} catch (Exception e) {
			ChannelCache.flush(host);
			throw wrap(e);
		}
	}

	@Override
	public IFileInfo fetchInfo(int options, IProgressMonitor monitor) throws CoreException {
		SynchronizedChannel channel = getChannel();
		try {
			SftpATTRS stat = channel.stat(getPathString(channel));
			cachedInfo = attrsToInfo(getName(), stat);
			return cachedInfo;
		} catch (Exception e) {
			ChannelCache.flush(host);
			throw wrap(e);
		}
	}

	/**
	 * Returns the channel for communicating with this file store.
	 */
	private SynchronizedChannel getChannel() throws CoreException {
		return ChannelCache.getChannel(host);
	}

	@Override
	public IFileStore getChild(String name) {
		return new SftpFileStore(host, path.append(name));
	}

	@Override
	public String getName() {
		return path.lastSegment();
	}

	@Override
	public IFileStore getParent() {
		if (path.equals(HOME)) {
			return null;
		}
		return new SftpFileStore(host, path.removeLastSegments(1));
	}

	@Override
	public IFileStore mkdir(int options, IProgressMonitor monitor) throws CoreException {
		SynchronizedChannel channel = getChannel();
		try {
			try {
				channel.mkdir(getPathString(channel));
			} catch (SftpException sftpException) {
				//jsch mkdir fails if dir already exists, but EFS API says we should not fail
				SftpATTRS stat = channel.stat(getPathString(channel));
				if (stat.isDir())
					return this;
				//rethrow and fail
				throw sftpException;
			}
		} catch (Exception e) {
			ChannelCache.flush(host);
			throw wrap(e);
		}
		return this;
	}

	@Override
	public InputStream openInputStream(int options, IProgressMonitor monitor) throws CoreException {
		SynchronizedChannel channel = getChannel();
		try {
			return new BufferedInputStream(channel.get(getPathString(channel)));
		} catch (Exception e) {
			ChannelCache.flush(host);
			throw wrap(e);
		}
	}

	@Override
	public OutputStream openOutputStream(int options, IProgressMonitor monitor) throws CoreException {
		SynchronizedChannel channel = getChannel();
		try {
			return new BufferedOutputStream(channel.put(getPathString(channel)));
		} catch (Exception e) {
			ChannelCache.flush(host);
			throw wrap(e);
		}
	}

	@Override
	public void putInfo(IFileInfo info, int options, IProgressMonitor monitor) throws CoreException {
		//not supported, but don't fail
	}

	@Override
	public void delete(int options, IProgressMonitor monitor) throws CoreException {
		SynchronizedChannel channel = getChannel();
		try {
			//we need to know if we are a directory or file, but used the last fetched info if available
			IFileInfo info = cachedInfo;
			//use local field in case of concurrent change to cached info
			if (info == null)
				info = fetchInfo();
			if (info.isDirectory())
				channel.rmdir(getPathString(channel));
			else
				channel.rm(getPathString(channel));
		} catch (Exception e) {
			ChannelCache.flush(host);
			throw wrap(e);
		}
	}

	/**
	 * Returns whether the given file name should be ignored by sftp file system
	 */
	protected boolean shouldSkip(String fileName) {
		//skip parent and self references
		if (".".equals(fileName) || "..".equals(fileName))//$NON-NLS-1$ //$NON-NLS-2$
			return true;
		return false;
	}

	@Override
	public URI toURI() {
		return URIUtil.append(host, path.toString());
	}

	/**
	 * Wraps a jsch exception in a form suitable to return to caller of EFS API.
	 */
	private CoreException wrap(Exception e) {
		String msg = NLS.bind("Failure connecting to {0}", host, e.getMessage());
		return new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e));
	}

}
