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
package org.eclipse.orion.internal.server.events;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.events.IFileChangeListener;
import org.eclipse.orion.server.core.events.IFileChangeNotificationService;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the Orion file change notification service. It has allows server side
 * processes to be notified when there are change events within the Orion server workspace.
 * There are separate events for create, update and delete events for both files and directories.
 *  
 * @author Anthony Hunter
 */
public class FileChangeNotificationService implements IFileChangeNotificationService {

	private Logger logger = LoggerFactory.getLogger(FileChangeNotificationService.class); //$NON-NLS-1$

	private List<IFileChangeListener> fileChangeListeners;

	private FileAlterationMonitor fileAlterationMonitor;

	private FileAlterationObserver fileAlterationObserver;

	/**
	 * A local listener that receives events from the apache common.io monitor and passes them on
	 * to the Orion servers file change listeners. 
	 */
	private class LocalFileAlterationListener extends FileAlterationListenerAdaptor {

		@Override
		public void onDirectoryCreate(File directory) {
			logger.info("file change notification service: Directory " + directory.getAbsolutePath() + " was created.");
			ProjectInfo projectInfo = getProject(directory);
			if (projectInfo != null) {
				IFileStore fileStore = getFileStore(directory);
				for (IFileChangeListener fileChangeListener : fileChangeListeners) {
					fileChangeListener.directoryCreated(fileStore, projectInfo);
				}
			}
		}

		@Override
		public void onDirectoryChange(File directory) {
			logger.info("file change notification service: Directory " + directory.getAbsolutePath() + " was changed.");
			ProjectInfo projectInfo = getProject(directory);
			if (projectInfo != null) {
				IFileStore fileStore = getFileStore(directory);
				for (IFileChangeListener fileChangeListener : fileChangeListeners) {
					fileChangeListener.directoryUpdated(fileStore, projectInfo);
				}
			}
		}

		@Override
		public void onDirectoryDelete(File directory) {
			logger.info("file change notification service: Directory " + directory.getAbsolutePath() + " was deleted.");
			ProjectInfo projectInfo = getProject(directory);
			if (projectInfo != null) {
				IFileStore fileStore = getFileStore(directory);
				for (IFileChangeListener fileChangeListener : fileChangeListeners) {
					fileChangeListener.directoryDeleted(fileStore, projectInfo);
				}
			}
		}

		@Override
		public void onFileCreate(File file) {
			logger.info("file change notification service: File " + file.getAbsoluteFile() + " was created.");
			ProjectInfo projectInfo = getProject(file);
			if (projectInfo != null) {
				IFileStore fileStore = getFileStore(file);
				for (IFileChangeListener fileChangeListener : fileChangeListeners) {
					fileChangeListener.fileCreated(fileStore, projectInfo);
				}
			}
		}

		@Override
		public void onFileChange(File file) {
			logger.info("file change notification service: File " + file.getAbsoluteFile() + " was changed.");
			ProjectInfo projectInfo = getProject(file);
			if (projectInfo != null) {
				IFileStore fileStore = getFileStore(file);
				for (IFileChangeListener fileChangeListener : fileChangeListeners) {
					fileChangeListener.fileUpdated(fileStore, projectInfo);
				}
			}
		}

		@Override
		public void onFileDelete(File file) {
			logger.info("file change notification service: File " + file.getAbsoluteFile() + " was deleted.");
			ProjectInfo projectInfo = getProject(file);
			if (projectInfo != null) {
				IFileStore fileStore = getFileStore(file);
				for (IFileChangeListener fileChangeListener : fileChangeListeners) {
					fileChangeListener.fileDeleted(fileStore, projectInfo);
				}
			}
		}

		private ProjectInfo getProject(File file) {
			try {
				IFileStore rootLocation = OrionConfiguration.getRootLocation();
				File rootLocationFile = rootLocation.toLocalFile(EFS.NONE, null);
				IPath path = new Path(file.getAbsolutePath().substring(rootLocationFile.getAbsolutePath().length() + 1));
				int segmentCount = path.segmentCount();
				if (segmentCount >= 5) {
					String username = path.segment(1);
					String projectName = path.segment(3);
					UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants2.USER_NAME, username, false, false);
					if (userInfo != null && !userInfo.getWorkspaceIds().isEmpty()) {
						String workspaceId = userInfo.getWorkspaceIds().get(0);
						ProjectInfo projectInfo = OrionConfiguration.getMetaStore().readProject(workspaceId, projectName);
						if (projectInfo != null) {
							return projectInfo;
						}
					}
				}
				return null;
			} catch (CoreException e) {
				logger.error(e.getLocalizedMessage(), e);
			}
			return null;
		}

		private IFileStore getFileStore(File file) {
			try {
				IFileStore fileStore = EFS.getStore(file.toURI());
				return fileStore;
			} catch (CoreException e) {
				logger.error(e.getLocalizedMessage(), e);
			}
			return null;
		}
	}

	public FileChangeNotificationService() {
		initializeFileChangeNotificationService();
	}

	/**
	 * Initialize the the file change notification service which currently does nothing until the first 
	 * listener is added.
	 */
	private void initializeFileChangeNotificationService() {
		fileChangeListeners = new ArrayList<IFileChangeListener>();
		fileAlterationObserver = null;
		fileAlterationMonitor = null;
	}

	public void addListener(IFileChangeListener listener) {
		if (fileAlterationMonitor == null) {
			// when the first listener is added, start the apache common.io monitor.
			try {
				startup();
			} catch (Exception e) {
				LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Failed to startup the file change notification service", e)); //$NON-NLS-1$
			}
		}
		fileChangeListeners.add(listener);
	}

	/**
	 * Shutdown the service by stopping the apache common.io monitor.
	 */
	public void shutdown() {
		if (fileAlterationMonitor != null) {
			try {
				fileAlterationMonitor.stop(2000L);
				fileAlterationMonitor.removeObserver(fileAlterationObserver);
				fileAlterationMonitor = null;
				fileAlterationObserver.destroy();
				fileAlterationObserver = null;
			} catch (Exception e) {
				LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Failed to shutdown the file change notification service", e)); //$NON-NLS-1$
			}
		}
	}

	/**
	 * startup the apache common.io monitor.
	 */
	private void startup() throws Exception {
		File serverworkspace = OrionConfiguration.getRootLocation().toLocalFile(EFS.NONE, null);
		IOFileFilter filter = FileFilterUtils.notFileFilter(FileFilterUtils.or(FileFilterUtils.prefixFileFilter(".metadata"), FileFilterUtils.prefixFileFilter(".archive")));
		fileAlterationObserver = new FileAlterationObserver(serverworkspace, filter);
		fileAlterationObserver.addListener(new LocalFileAlterationListener());
		fileAlterationMonitor = new FileAlterationMonitor(1000L);
		fileAlterationMonitor.addObserver(fileAlterationObserver);
		fileAlterationMonitor.start();
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.core"); //$NON-NLS-1$
		logger.info("Started the file change notification service");
	}

	public void removeListener(IFileChangeListener listener) {
		fileChangeListeners.add(listener);
	}
}
