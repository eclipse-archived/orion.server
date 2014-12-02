/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.sync;

import java.io.*;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.commands.StartDebugAppCommand;
import org.eclipse.orion.server.cf.commands.StopDebugAppCommand;
import org.eclipse.orion.server.cf.sync.cflauncher.commands.*;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.events.IFileChangeListener;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileChangeListener implements IFileChangeListener {

	private final Logger logger = LoggerFactory.getLogger(CFActivator.PI_CF); //$NON-NLS-1$

	@Override
	public void directoryCreated(IFileStore directory, ProjectInfo projectInfo) {
		logger.debug("Sync: creating " + directory.getName() + " in " + projectInfo.getFullName());

		try {
			long time = System.currentTimeMillis();

			Combo combo = findLaunchConfiguration(directory);
			JSONObject launchConfiguration = combo.launchConfiguration;
			String path = combo.path;
			if (launchConfiguration == null || !launchConfiguration.has("Url"))
				return;

			String url = launchConfiguration.getString("Url").replace("http://", "");

			CreateFolderCommand updateFileCommand = new CreateFolderCommand(url, path);
			ServerStatus updateFileStatus = (ServerStatus) updateFileCommand.doIt();
			if (updateFileStatus.isOK()) {
				restartApp(url);
			} else {
				logger.error("Sync: problem creating folder at " + url);
			}

			logger.debug("Sync: folder create took " + (System.currentTimeMillis() - time) + "ms");
		} catch (Exception e) {
			logger.error("Sync: folder create failed", e);
		}
	}

	@Override
	public void directoryUpdated(IFileStore directory, ProjectInfo projectInfo) {
		logger.error("Sync: directory updated event for " + directory.getName() + " in " + projectInfo.getFullName() + " NOT supported");
	}

	@Override
	public void directoryDeleted(IFileStore directory, ProjectInfo projectInfo) {
		this.fileDeleted(directory, projectInfo);
	}

	@Override
	public void fileCreated(IFileStore file, ProjectInfo projectInfo) {
		this.fileUpdated(file, projectInfo);
	}

	@Override
	public void fileDeleted(IFileStore file, ProjectInfo projectInfo) {
		logger.debug("Sync: deleting " + file.getName() + " in " + projectInfo.getFullName());

		try {
			long time = System.currentTimeMillis();

			Combo combo = findLaunchConfiguration(file);
			JSONObject launchConfiguration = combo.launchConfiguration;
			String path = combo.path;
			if (launchConfiguration == null || !launchConfiguration.has("Url"))
				return;

			String url = launchConfiguration.getString("Url").replace("http://", "");

			DeleteFileCommand deleteFileCommand = new DeleteFileCommand(url, path);
			ServerStatus updateFileStatus = (ServerStatus) deleteFileCommand.doIt();
			if (updateFileStatus.isOK()) {
				restartApp(url);
			} else {
				logger.error("Sync: problem deleting the file at " + url);
			}

			logger.debug("Sync: file delete took " + (System.currentTimeMillis() - time) + "ms");
		} catch (Exception e) {
			logger.error("Sync: file delete failed", e);
		}
	}

	@Override
	public void fileUpdated(IFileStore file, ProjectInfo projectInfo) {
		logger.debug("Sync: updating " + file.getName() + " in " + projectInfo.getFullName());

		try {
			long time = System.currentTimeMillis();

			Combo combo = findLaunchConfiguration(file);
			JSONObject launchConfiguration = combo.launchConfiguration;
			String path = combo.path;
			if (launchConfiguration == null || !launchConfiguration.has("Url"))
				return;

			String url = launchConfiguration.getString("Url").replace("http://", "");

			byte[] content = getContent(file.openInputStream(EFS.NONE, null));
			UpdateFileCommand updateFileCommand = new UpdateFileCommand(url, path, content);
			ServerStatus updateFileStatus = (ServerStatus) updateFileCommand.doIt();
			if (updateFileStatus.isOK()) {
				restartApp(url);
			} else {
				logger.error("Sync: problem updating the file at " + url);
			}

			logger.debug("Sync: file update took " + (System.currentTimeMillis() - time) + "ms");
		} catch (Exception e) {
			logger.error("Sync: file update failed", e);
		}
	}

	private void restartApp(String url) {
		logger.debug("Sync: trying to restart the app at " + url);

		StopDebugAppCommand stopDebug = new StopDebugAppCommand(url);
		ServerStatus stopDebugStatus = (ServerStatus) stopDebug.doIt();
		if (!stopDebugStatus.isOK())
			logger.error("Sync: problem stopping the app at " + url);

		StartDebugAppCommand startDebug = new StartDebugAppCommand(url);
		ServerStatus startDebugStatus = (ServerStatus) startDebug.doIt();
		if (!startDebugStatus.isOK())
			logger.error("Sync: problem starting the app at " + url);
	}

	private Combo findLaunchConfiguration(IFileStore file) throws Exception {
		IFileStore folder = file;
		IFileStore project = null;
		String path = file.getName();
		while ((folder = folder.getParent()) != null && project == null) {
			String[] childNames = folder.childNames(EFS.NONE, null);
			for (int i = 0; i < childNames.length; i++) {
				if (childNames[i].equals("project.json")) {
					project = folder;
					break;
				}
			}

			if (project == null)
				path = folder.getName() + "/" + path;
		}

		if (project == null)
			return null;

		return new Combo(path, readLaunchConfiguration(project.getChild("launchConfigurations")));
	}

	private class Combo {
		String path;
		JSONObject launchConfiguration;

		Combo(String path, JSONObject launchConfiguration) {
			this.path = path;
			this.launchConfiguration = launchConfiguration;
		}
	}

	private JSONObject readLaunchConfiguration(IFileStore launchConfStore) throws Exception {
		String[] childNames = launchConfStore.childNames(EFS.NONE, null);
		for (int i = 0; i < childNames.length; i++) {
			if (childNames[i].endsWith(".launch")) {
				byte[] content = getContent(launchConfStore.getChild(childNames[i]).openInputStream(EFS.NONE, null));
				return new JSONObject(new String(content));
			}
		}
		return new JSONObject();
	}

	private byte[] getContent(InputStream responseStream) throws IOException {
		try {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			int readBytes;
			byte[] data = new byte[1024];
			while ((readBytes = responseStream.read(data, 0, data.length)) != -1) {
				buffer.write(data, 0, readBytes);
			}

			byte[] content = buffer.toByteArray();
			return content;
		} finally {
			responseStream.close();
		}
	}
}
