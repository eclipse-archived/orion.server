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
import org.eclipse.orion.server.cf.commands.*;
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
		// TODO Auto-generated method stub

	}

	@Override
	public void directoryDeleted(IFileStore directory, ProjectInfo projectInfo) {
		// TODO Auto-generated method stub

	}

	@Override
	public void directoryUpdated(IFileStore directory, ProjectInfo projectInfo) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fileCreated(IFileStore file, ProjectInfo projectInfo) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fileDeleted(IFileStore file, ProjectInfo projectInfo) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fileUpdated(IFileStore file, ProjectInfo projectInfo) {
		logger.debug("Sync: updating " + file.getName() + " in " + projectInfo.getFullName());

		try {
			long time = System.currentTimeMillis();

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
				return;

			JSONObject launchConfiguration = getLaunchConfiguration(project.getChild("launchConfigurations"));
			if (!launchConfiguration.has("Url"))
				return;

			String url = launchConfiguration.getString("Url").replace("http://", "");

			byte[] content = getContent(file.openInputStream(EFS.NONE, null));
			UpdateFileInAppCommand updateFileCommand = new UpdateFileInAppCommand(url, path, content);
			ServerStatus updateFileStatus = (ServerStatus) updateFileCommand.doIt();
			if (updateFileStatus.isOK()) {
				logger.debug("Sync: trying to restart the app at " + url);

				StopDebugAppCommand stopDebug = new StopDebugAppCommand(url);
				ServerStatus stopDebugStatus = (ServerStatus) stopDebug.doIt();
				if (!stopDebugStatus.isOK())
					logger.error("Sync: problem stopping the app at " + url);

				StartDebugAppCommand startDebug = new StartDebugAppCommand(url);
				ServerStatus startDebugStatus = (ServerStatus) startDebug.doIt();
				if (!stopDebugStatus.isOK())
					logger.error("Sync: problem starting the app at " + url);
			} else {
				logger.error("Sync: problem updating the app at " + url);
			}

			logger.debug("Sync: file update took " + (System.currentTimeMillis() - time) + "ms");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private JSONObject getLaunchConfiguration(IFileStore launchConfStore) throws Exception {
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
