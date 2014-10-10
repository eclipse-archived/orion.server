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
package org.eclipse.orion.server.cf.commands;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.*;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetOrgsCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	public GetOrgsCommand(String userId, Target target) {
		super(target);
		this.commandName = "Get Spaces";
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			/* get available orgs */
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI orgsURI = targetURI.resolve("/v2/organizations");

			GetMethod getDomainsMethod = new GetMethod(orgsURI.toString());
			HttpUtil.configureHttpMethod(getDomainsMethod, target);
			getDomainsMethod.setQueryString("inline-relations-depth=1"); //$NON-NLS-1$

			ServerStatus status = HttpUtil.executeMethod(getDomainsMethod);
			if (!status.isOK())
				return status;

			/* extract available orgs */
			JSONObject orgs = status.getJsonData();

			if (orgs == null || orgs.optInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS, 0) < 1) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NO_CONTENT, "Server did not return any organizations.", null);
			}

			/* look if the domain is available */
			JSONObject result = new JSONObject();
			int resources = orgs.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).length();
			for (int k = 0; k < resources; ++k) {
				JSONObject orgJSON = orgs.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).getJSONObject(k);
				List<Space> spaces = new ArrayList<Space>();
				status = getSpaces(spaces, orgJSON);
				if (!status.isOK())
					return status;
				OrgWithSpaces orgWithSpaces = new OrgWithSpaces();
				orgWithSpaces.setCFJSON(orgJSON);
				orgWithSpaces.setSpaces(spaces);
				result.append("Orgs", orgWithSpaces.toJSON());
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
		} catch (ConnectTimeoutException e) {
			String msg = NLS.bind("An error occurred when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_GATEWAY_TIMEOUT, msg, e);
		} catch (Exception e) {
			String msg = NLS.bind("An error occurred when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	private ServerStatus getSpaces(List<Space> spaces, JSONObject orgJSON) throws Exception {
		long time = System.currentTimeMillis();
		URI targetURI = URIUtil.toURI(target.getUrl());
		URI spaceURI = targetURI.resolve(orgJSON.getJSONObject("entity").getString("spaces_url"));

		GetMethod getDomainsMethod = new GetMethod(spaceURI.toString());
		HttpUtil.configureHttpMethod(getDomainsMethod, target);
		getDomainsMethod.setQueryString("inline-relations-depth=1"); //$NON-NLS-1$

		ServerStatus status = HttpUtil.executeMethod(getDomainsMethod);
		if (!status.isOK())
			return status;

		/* extract available spaces */
		JSONObject orgs = status.getJsonData();

		if (orgs.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1) {
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
		}

		/* look if the domain is available */
		int resources = orgs.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).length();
		for (int k = 0; k < resources; ++k) {
			JSONObject spaceJSON = orgs.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).getJSONObject(k);
			spaces.add(new Space().setCFJSON(spaceJSON));
		}

		logger.debug("GetOrgsCommand: getting spaces using " + spaceURI + " took " + (System.currentTimeMillis() - time));

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}
}
