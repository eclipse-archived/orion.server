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

import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.MultipartPostMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushAppCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	private Target target;
	private App app;

	public PushAppCommand(Target target, App app) {
		this.commandName = "Set Org"; //$NON-NLS-1$
		this.target = target;
		this.app = app;
	}

	public IStatus doIt() {
		IStatus status = validateParams();
		if (!status.isOK())
			return status;

		try {
			URI infoURI = URIUtil.toURI(target.getUrl());

			infoURI = infoURI.resolve("/v2/organizations");

			GetMethod getMethod = new GetMethod(infoURI.toString());
			getMethod.addRequestHeader(new Header("Accept", "application/json"));
			getMethod.addRequestHeader(new Header("Content-Type", "application/json"));
			getMethod.addRequestHeader(new Header("Authorization", "bearer " + target.getAccessToken().getString("access_token")));
			CFActivator.getDefault().getHttpClient().executeMethod(getMethod);

			MultipartPostMethod d = new MultipartPostMethod();

			String response = getMethod.getResponseBodyAsString();
			JSONObject result = new JSONObject(response);

			JSONArray orgs = result.getJSONArray("resources");
			JSONObject org = orgs.getJSONObject(0);
			target.setOrg(org);
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
}
