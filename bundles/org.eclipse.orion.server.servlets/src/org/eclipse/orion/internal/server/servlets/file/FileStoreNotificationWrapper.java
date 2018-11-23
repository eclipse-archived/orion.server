/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.IFileSystem;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.orion.internal.server.servlets.ChangeEvent;
import org.eclipse.orion.internal.server.servlets.IFileStoreModificationListener.ChangeType;

/**
 * Wraps an ordinary {@link IFileStore} to provide notifications after write operations have 
 * occurred. Supports {@link #equals(Object)} and {@link #hashCode()}.
 */
public class FileStoreNotificationWrapper implements IFileStore {

	private void notifyOfWrite(ChangeEvent event) {
		FilesystemModificationListenerManager.getInstance().notifyOfChange(event);
	}

	/**
	 * Wrap the given store. When the wrapper is used to perform writes, change events
	 * will be generated. 
	 */
	public static FileStoreNotificationWrapper wrap(Object source, IFileStore store) {
		if (store == null) {
			return null;
		}

		return new FileStoreNotificationWrapper(source, store);
	}

	/** The source value for ChangeEvent notifications. */
	private final Object source;

	private final IFileStore wrapped;

	private FileStoreNotificationWrapper(Object source, IFileStore store) {
		this.source = source;
		this.wrapped = store;
	}

	@SuppressWarnings("rawtypes")
	public Object getAdapter(Class adapter) {
		return wrapped.getAdapter(adapter);
	}

	public IFileInfo[] childInfos(int options, IProgressMonitor monitor) throws CoreException {
		return wrapped.childInfos(options, monitor);
	}

	public String[] childNames(int options, IProgressMonitor monitor) throws CoreException {
		return wrapped.childNames(options, monitor);
	}

	public IFileStore[] childStores(int options, IProgressMonitor monitor) throws CoreException {
		IFileStore[] toWrap = wrapped.childStores(options, monitor);

		for (int i = 0; i < toWrap.length; i++) {
			toWrap[i] = wrap(source, toWrap[i]);
		}

		return toWrap;
	}

	public void copy(IFileStore destination, int options, IProgressMonitor monitor) throws CoreException {
		destination = unwrap(destination);

		wrapped.copy(destination, options, monitor);

		// Tested by CoreFilesTest.testListenerCopyFile()
		notifyOfWrite(new ChangeEvent(source, ChangeType.COPY_INTO, destination, wrapped));
	}

	public void delete(int options, IProgressMonitor monitor) throws CoreException {
		wrapped.delete(options, monitor);

		// Tested by CoreFilesTest.testListenerDeleteNonEmptyDirectory()
		// Tested by CoreFilesTest.testListenerDeleteEmptyDir()
		// Tested by CoreFilesTest.testListenerDeleteFile()
		notifyOfWrite(new ChangeEvent(source, ChangeType.DELETE, wrapped));
	}

	public IFileInfo fetchInfo() {
		return wrapped.fetchInfo();
	}

	public IFileInfo fetchInfo(int options, IProgressMonitor monitor) throws CoreException {
		return wrapped.fetchInfo(options, monitor);
	}

	@SuppressWarnings("deprecation")
	public IFileStore getChild(IPath path) {
		return wrap(source, wrapped.getChild(path));
	}

	public IFileStore getFileStore(IPath path) {
		return wrap(source, wrapped.getFileStore(path));
	}

	public IFileStore getChild(String name) {
		return wrap(source, wrapped.getChild(name));
	}

	public IFileSystem getFileSystem() {
		return wrapped.getFileSystem();
	}

	public String getName() {
		return wrapped.getName();
	}

	public IFileStore getParent() {
		return wrap(source, wrapped.getParent());
	}

	public boolean isParentOf(IFileStore other) {
		other = unwrap(other);

		return wrapped.isParentOf(other);
	}

	public static IFileStore unwrap(IFileStore other) {
		if (other instanceof FileStoreNotificationWrapper) {
			other = ((FileStoreNotificationWrapper) other).wrapped;
		}
		return other;
	}

	public IFileStore mkdir(int options, IProgressMonitor monitor) throws CoreException {
		IFileStore mkdir = wrapped.mkdir(options, monitor);

		notifyOfWrite(new ChangeEvent(source, ChangeType.MKDIR, mkdir));

		// Tested by CoreFilesTest.testListenerCreateDirectory()
		return wrap(source, mkdir);
	}

	public void move(IFileStore destination, int options, IProgressMonitor monitor) throws CoreException {
		destination = unwrap(destination);

		wrapped.move(destination, options, monitor);

		// Tested by CoreFilesTest.testListenerMoveFileNoOverwrite()
		notifyOfWrite(new ChangeEvent(source, ChangeType.MOVE, destination, wrapped));
	}

	public InputStream openInputStream(int options, IProgressMonitor monitor) throws CoreException {
		return wrapped.openInputStream(options, monitor);
	}

	public OutputStream openOutputStream(int options, IProgressMonitor monitor) throws CoreException {
		final OutputStream out = wrapped.openOutputStream(options, monitor);

		return new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				out.write(b);
			}

			@Override
			public void write(byte[] b) throws IOException {
				out.write(b);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				out.write(b, off, len);
			}

			@Override
			public void flush() throws IOException {
				out.flush();
			}

			@Override
			public void close() throws IOException {
				try {
					out.close();
				} finally {
					// Tested by CoreFilesTest.testListenerWriteFile()
					notifyOfWrite(new ChangeEvent(source, ChangeType.WRITE, wrapped));
				}
			}
		};
	}

	public void putInfo(IFileInfo info, int options, IProgressMonitor monitor) throws CoreException {
		wrapped.putInfo(info, options, monitor);

		// Tested by Ad
		notifyOfWrite(new ChangeEvent(source, ChangeType.PUTINFO, wrapped));
	}

	public File toLocalFile(int options, IProgressMonitor monitor) throws CoreException {
		// ;_;  so much for wrapping
		return wrapped.toLocalFile(options, monitor);
	}

	public URI toURI() {
		return wrapped.toURI();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof FileStoreNotificationWrapper) {
			if (wrapped.equals(unwrap((FileStoreNotificationWrapper) o))) {
				return true;
			}
		}

		return false;
	}

	@Override
	public int hashCode() {
		return wrapped.hashCode();
	}
}
