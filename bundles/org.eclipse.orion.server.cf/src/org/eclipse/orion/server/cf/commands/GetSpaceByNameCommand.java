/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others 
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
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.objects.Org;
import org.eclipse.orion.server.cf.objects.Space;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetSpaceByNameCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String spaceName;
	private String orgName;

	public GetSpaceByNameCommand(Target target, String orgName, String spaceName) {
		super(target);
		this.orgName = orgName;
		this.spaceName = spaceName;
		this.commandName = "Get Space By Name"; //$NON-NLS-1$
	}

	/**
	 * Attempts to find the given space.
	 * 
	 * @param orgs
	 *            The available non-empty space array
	 * @param orgSpace
	 *            The space to find
	 * @throws JSONException
	 */
	protected Space getSpace(JSONArray spaces, String orgSpace) throws JSONException {
		for (int i = 0; i < spaces.length(); i++) {
			JSONObject spaceJSON = spaces.getJSONObject(i);
			if (orgSpace.equals(spaceJSON.getJSONObject("entity").getString("name"))) //$NON-NLS-1$ //$NON-NLS-2$
				return new Space().setCFJSON(spaceJSON);
		}

		return null;
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
			if (organization.equals(orgJSON.getJSONObject("entity").getString("name")))
				return new Org().setCFJSON(orgJSON);
		}

		return null;
	}

	public ServerStatus _doIt() {
		try {
			
			// check the org first
			
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI orgsURI = targetURI.resolve("/v2/organizations"); //$NON-NLS-1$

			GetMethod getOrgsMethod = new GetMethod(orgsURI.toString());
			ServerStatus confStatus = HttpUtil.configureHttpMethod(getOrgsMethod, target.getCloud());
			if (!confStatus.isOK())
				return confStatus;
			
			ServerStatus getOrgsStatus = HttpUtil.executeMethod(getOrgsMethod);
			if (!getOrgsStatus.isOK() && getOrgsStatus.getHttpCode() != HttpServletResponse.SC_PARTIAL_CONTENT)
				return getOrgsStatus;

			JSONObject result = getOrgsStatus.getJsonData();
			JSONArray orgs = result.getJSONArray("resources"); //$NON-NLS-1$

			if (orgs.length() == 0)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Organization not found", null);

			Org organization = getOrganization(orgs, orgName);
			if (organization == null)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Organization not found", null);
			
			// now get the space
			
			String spaceUrl = organization.getCFJSON().getJSONObject("entity").getString("spaces_url"); //$NON-NLS-1$//$NON-NLS-2$
			URI spaceURI = targetURI.resolve(spaceUrl);

			GetMethod getSpaceMethod = new GetMethod(spaceURI.toString());
			confStatus = HttpUtil.configureHttpMethod(getSpaceMethod, target.getCloud());
			if (!confStatus.isOK())
				return confStatus;

			ServerStatus getSpaceStatus = HttpUtil.executeMethod(getSpaceMethod);
			if (!getSpaceStatus.isOK())
				return getSpaceStatus;

			result = getSpaceStatus.getJsonData();
			JSONArray spaces = result.getJSONArray("resources"); //$NON-NLS-1$

			if (spaces.length() == 0)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Space not found", null);

			Space space = getSpace(spaces, spaceName);
			if (space == null)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Space not found", null);

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, space.toJSON());
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
