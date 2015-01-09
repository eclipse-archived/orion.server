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
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetOrgCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private boolean isGuid = false;
	private String defaultOrg;
	private String commandName;
	private String org;

	public SetOrgCommand(Target target, String orgName) {
		super(target);
		this.org = orgName;
		this.defaultOrg = null;
		this.commandName = "Set Org"; //$NON-NLS-1$
	}

	public SetOrgCommand(Target target, String org, boolean isGuid) {
		super(target);
		this.org = org;
		this.isGuid = isGuid;
		this.defaultOrg = null;
		this.commandName = "Set Org"; //$NON-NLS-1$
	}

	/**
	 * Defines the default organization to choose in case no specific organization
	 *  is requested using the {@link SetOrgCommand} constructor.
	 * @param defaultOrg
	 */
	public void setDefaultOrg(String defaultOrg) {
		this.defaultOrg = defaultOrg;
	}

	/**
	 * Attempts to find the given organization.
	 * @param orgs The available non-empty organization array
	 * @param organization The organization to find
	 * @throws JSONException
	 */
	protected Org getOrganization(JSONArray orgs, String organization) throws JSONException {
		for (int i = 0; i < orgs.length(); ++i) {
			JSONObject orgJSON = orgs.getJSONObject(i);
			if ((!isGuid && organization.equals(orgJSON.getJSONObject("entity").getString("name"))) || (isGuid && organization.equals(orgJSON.getJSONObject("metadata").getString("guid")))) //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$
				return new Org().setCFJSON(orgJSON);
		}

		return null;
	}

	/**
	 * Returns an arbitrary organization.
	 * @param orgs The available non-empty organization array
	 * @throws JSONException
	 */
	protected Org getArbitraryOrganization(JSONArray orgs) throws JSONException {
		JSONObject org = orgs.getJSONObject(0);
		return new Org().setCFJSON(org);
	}

	public ServerStatus _doIt() {
		try {

			URI infoURI = URIUtil.toURI(target.getUrl());
			infoURI = infoURI.resolve("/v2/organizations"); //$NON-NLS-1$

			GetMethod getMethod = new GetMethod(infoURI.toString());
			HttpUtil.configureHttpMethod(getMethod, target);
			ServerStatus getStatus = HttpUtil.executeMethod(getMethod);
			if (!getStatus.isOK())
				return getStatus;

			JSONObject result = getStatus.getJsonData();
			JSONArray orgs = result.getJSONArray("resources"); //$NON-NLS-1$

			if (orgs.length() == 0)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Organization not found", null);

			if (org == null || "".equals(org)) { //$NON-NLS-1$

				Org organization = null;
				if (defaultOrg != null) {
					organization = getOrganization(orgs, defaultOrg);
					if (organization == null)
						organization = getArbitraryOrganization(orgs);
				} else
					organization = getArbitraryOrganization(orgs);

				target.setOrg(organization);

			} else {
				Org organization = getOrganization(orgs, org);
				target.setOrg(organization);
			}

			if (target.getOrg() == null)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Organization not found", null);

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, target.getOrg().toJSON());

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
