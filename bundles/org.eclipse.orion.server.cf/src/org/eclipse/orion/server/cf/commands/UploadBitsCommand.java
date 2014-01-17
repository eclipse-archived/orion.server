/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.commands;

import java.io.*;
import java.net.URI;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.multipart.*;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UploadBitsCommand extends AbstractCFCommand {
	private static final int MAX_ATTEMPTS = 150;
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App application;

	public UploadBitsCommand(String userId, Target target, App app) {
		super(target, userId);

		String[] bindings = {app.getName(), app.getGuid()};
		this.commandName = NLS.bind("Upload application {0} bits (guid: {1})", bindings);

		this.application = app;
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			/* upload project contents */
			File zippedApplication = zipApplication(application.getContentLocation());
			if (zippedApplication == null)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to read application content", null);

			URI targetURI = URIUtil.toURI(target.getUrl());
			PutMethod uploadMethod = new PutMethod(targetURI.resolve("/v2/apps/" + application.getGuid() + "/bits?async=true").toString());
			uploadMethod.addRequestHeader(new Header("Authorization", "bearer " + target.getAccessToken().getString("access_token")));

			Part[] parts = {new StringPart(CFProtocolConstants.V2_KEY_RESOURCES, "[]"), new FilePart(CFProtocolConstants.V2_KEY_APPLICATION, zippedApplication)};
			uploadMethod.setRequestEntity(new MultipartRequestEntity(parts, uploadMethod.getParams()));

			/* send request */
			ServerStatus jobStatus = HttpUtil.executeMethod(uploadMethod);
			if (!jobStatus.isOK())
				return jobStatus;

			/* long running task, keep track */
			int attemptsLeft = MAX_ATTEMPTS;
			JSONObject resp = jobStatus.getJsonData();
			String taksStatus = resp.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_STATUS);
			while (!CFProtocolConstants.V2_KEY_FINISHED.equals(taksStatus) && !CFProtocolConstants.V2_KEY_FAILURE.equals(taksStatus)) {

				if (attemptsLeft == 0) {
					/* delete the tmp file */
					zippedApplication.delete();
					return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Upload timeout exceeded", null);
				}

				/* two seconds */
				Thread.sleep(2000);

				/* check whether job has finished */
				URI jobLocation = targetURI.resolve(resp.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_URL));
				GetMethod jobRequest = new GetMethod(jobLocation.toString());
				HttpUtil.configureHttpMethod(jobRequest, target);

				/* send request */
				jobStatus = HttpUtil.executeMethod(jobRequest);
				if (!jobStatus.isOK())
					return jobStatus;

				resp = jobStatus.getJsonData();
				taksStatus = resp.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_STATUS);

				--attemptsLeft;
			}

			if (CFProtocolConstants.V2_KEY_FAILURE.equals(jobStatus))
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to upload application bits", null);

			/* delete the tmp file */
			zippedApplication.delete();

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	private File zipApplication(String sourcePath) throws IOException, CoreException {
		/* get the underlying file store */
		IFileStore fileStore = NewFileServlet.getFileStore(null, new Path(sourcePath).removeFirstSegments(1));
		if (fileStore == null)
			return null;

		/* zip application to a temporary file */
		String randomName = UUID.randomUUID().toString();
		File tmp = File.createTempFile(randomName, ".zip");

		try {
			ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmp));
			writeZip(fileStore, Path.EMPTY, zos);
			zos.close();
		} catch (Exception ex) {
			/* delete corrupted zip file */
			tmp.delete();
		}

		return tmp;
	}

	/* recursively zip the entire directory */
	private void writeZip(IFileStore source, IPath path, ZipOutputStream zos) throws IOException, CoreException {
		IFileInfo info = source.fetchInfo(EFS.NONE, null);
		if (info.isDirectory()) {
			for (IFileStore child : source.childStores(EFS.NONE, null))
				writeZip(child, path.append(child.getName()), zos);
		} else {
			ZipEntry entry = new ZipEntry(path.toString());
			zos.putNextEntry(entry);
			IOUtilities.pipe(source.openInputStream(EFS.NONE, null), zos, true, false);
		}
	}
}
