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
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.objects.Org;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetOrgCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String orgName;

	public SetOrgCommand(String userId, Target target, String orgName) {
		super(target, userId);
		this.commandName = "Set Org"; //$NON-NLS-1$
		this.orgName = orgName;
	}

	public ServerStatus _doIt() {
		try {
			URI infoURI = URIUtil.toURI(target.getUrl());

			infoURI = infoURI.resolve("/v2/organizations");

			GetMethod getMethod = new GetMethod(infoURI.toString());
			HttpUtil.configureHttpMethod(getMethod, target);
			ServerStatus getStatus = HttpUtil.executeMethod(getMethod);
			if (!getStatus.isOK())
				return getStatus;

			JSONObject result = getStatus.getJsonData();
			JSONArray orgs = result.getJSONArray("resources");

			if (orgs.length() == 0) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Organization not found", null);
			}

			if (this.orgName == null || "".equals(this.orgName)) {
				JSONObject org = orgs.getJSONObject(0);
				target.setOrg(new Org().setCFJSON(org));
			} else {
				for (int i = 0; i < orgs.length(); i++) {
					JSONObject org = orgs.getJSONObject(i);
					if (orgName.equals(org.getJSONObject("entity").getString("name")))
						target.setOrg(new Org().setCFJSON(org));
				}
			}

			if (target.getOrg() == null) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Organization not found", null);
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, target.getOrg().toJSON());
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
