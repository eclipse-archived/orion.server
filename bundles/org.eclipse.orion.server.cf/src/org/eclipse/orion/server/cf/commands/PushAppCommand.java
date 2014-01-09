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
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.methods.multipart.*;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushAppCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	private Target target;
	private App app;

	public PushAppCommand(Target target, App app) {
		this.commandName = "Push application"; //$NON-NLS-1$
		this.target = target;
		this.app = app;
	}

	public IStatus doIt() {
		IStatus status = validateParams();
		if (!status.isOK())
			return status;

		try {
			/* create cloud foundry application */
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI appsURI = targetURI.resolve("/v2/apps");

			PostMethod createAppMethod = new PostMethod(appsURI.toString());
			HttpUtil.configureHttpMethod(createAppMethod, target);

			/* set request body */
			JSONObject createAppRequst = new JSONObject();
			createAppRequst.put(CFProtocolConstants.V2_KEY_SPACE_GUID, target.getSpace().getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID));
			createAppRequst.put(CFProtocolConstants.V2_KEY_NAME, this.app.getName());
			createAppRequst.put(CFProtocolConstants.V2_KEY_INSTANCES, 1);
			createAppRequst.put(CFProtocolConstants.V2_KEY_BUILDPACK, JSONObject.NULL);
			createAppRequst.put(CFProtocolConstants.V2_KEY_COMMAND, "node app.js");
			createAppRequst.put(CFProtocolConstants.V2_KEY_MEMORY, 128);
			createAppRequst.put(CFProtocolConstants.V2_KEY_STACK_GUID, JSONObject.NULL);
			createAppMethod.setRequestEntity(new StringRequestEntity(createAppRequst.toString(), "application/json", "utf-8"));

			/* send request */
			int responseCode = CFActivator.getDefault().getHttpClient().executeMethod(createAppMethod);

			if (responseCode != HttpStatus.SC_CREATED)
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_BAD_REQUEST);

			/* extract application guid */
			JSONObject resp = new JSONObject(createAppMethod.getResponseBodyAsString());
			String appGUID = resp.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);

			/* get available domains */
			String domainsURL = target.getSpace().getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_DOMAINS_URL);
			URI domainsURI = targetURI.resolve(domainsURL);

			GetMethod getDomainsMethod = new GetMethod(domainsURI.toString());
			HttpUtil.configureHttpMethod(getDomainsMethod, target);
			getDomainsMethod.setQueryString("inline-relations-depth=1");

			/* send request */
			responseCode = CFActivator.getDefault().getHttpClient().executeMethod(getDomainsMethod);
			if (responseCode != HttpStatus.SC_OK)
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

			/* extract available domains */
			JSONObject domains = new JSONObject(getDomainsMethod.getResponseBodyAsString());

			/* get first available domain */
			if (domains.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1)
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

			String domainGUID = domains.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).getJSONObject(0).getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);

			/* new application, we do not need to check for attached routes, create a new one */
			URI routesURI = targetURI.resolve("/v2/routes");
			PostMethod createRouteMethod = new PostMethod(routesURI.toString());
			HttpUtil.configureHttpMethod(createRouteMethod, target);

			/* set request body */
			JSONObject routeRequest = new JSONObject();
			routeRequest.put(CFProtocolConstants.V2_KEY_SPACE_GUID, target.getSpace().getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID));
			routeRequest.put(CFProtocolConstants.V2_KEY_HOST, this.app.getName() + "-" + UUID.randomUUID());
			routeRequest.put(CFProtocolConstants.V2_KEY_DOMAIN_GUID, domainGUID);
			createRouteMethod.setRequestEntity(new StringRequestEntity(routeRequest.toString(), "application/json", "utf-8"));

			/* send request */
			responseCode = CFActivator.getDefault().getHttpClient().executeMethod(createRouteMethod);
			if (responseCode != HttpStatus.SC_CREATED)
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_BAD_REQUEST);

			/* extract route guid */
			JSONObject route = new JSONObject(createRouteMethod.getResponseBodyAsString());
			String routeGUID = route.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);

			/* attach route to application */
			PutMethod attachRouteMethod = new PutMethod(targetURI.resolve("/v2/apps/" + appGUID + "/routes/" + routeGUID).toString());
			HttpUtil.configureHttpMethod(attachRouteMethod, target);

			responseCode = CFActivator.getDefault().getHttpClient().executeMethod(attachRouteMethod);
			if (responseCode != HttpStatus.SC_CREATED)
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

			/* upload project contents */
			File zippedApplication = zipApplication(this.app.getContentLocation());
			if (zippedApplication == null)
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

			PutMethod uploadMethod = new PutMethod(targetURI.resolve("/v2/apps/" + appGUID + "/bits?async=true").toString());
			uploadMethod.addRequestHeader(new Header("Authorization", "bearer " + target.getAccessToken().getString("access_token")));

			Part[] parts = {new StringPart(CFProtocolConstants.V2_KEY_RESOURCES, "[]"), new FilePart(CFProtocolConstants.V2_KEY_APPLICATION, zippedApplication)};
			uploadMethod.setRequestEntity(new MultipartRequestEntity(parts, uploadMethod.getParams()));

			/* send request */
			responseCode = CFActivator.getDefault().getHttpClient().executeMethod(uploadMethod);
			if (responseCode != HttpStatus.SC_CREATED)
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_BAD_REQUEST);

			/* long running task, keep track */
			resp = new JSONObject(uploadMethod.getResponseBodyAsString());
			String jobStatus = resp.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_STATUS);
			while (!CFProtocolConstants.V2_KEY_FINISHED.equals(jobStatus) && !CFProtocolConstants.V2_KEY_FAILURE.equals(jobStatus)) {

				/* two seconds */
				Thread.sleep(2000);

				/* check whether job has finished */
				URI jobLocation = targetURI.resolve(resp.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_URL));
				GetMethod jobRequest = new GetMethod(jobLocation.toString());
				HttpUtil.configureHttpMethod(jobRequest, target);

				/* send request */
				responseCode = CFActivator.getDefault().getHttpClient().executeMethod(jobRequest);
				if (responseCode != HttpStatus.SC_OK)
					return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

				resp = new JSONObject(jobRequest.getResponseBodyAsString());
				jobStatus = resp.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_STATUS);
			}

			if (CFProtocolConstants.V2_KEY_FAILURE.equals(jobStatus))
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_BAD_REQUEST);

			/* delete the tmp file */
			zippedApplication.delete();

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new Status(IStatus.ERROR, CFActivator.PI_CF, msg, e);
		}

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}

	private IStatus validateParams() {
		return Status.OK_STATUS;
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
