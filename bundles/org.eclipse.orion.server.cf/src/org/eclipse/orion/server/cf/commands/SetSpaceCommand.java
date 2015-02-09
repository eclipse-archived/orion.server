/*******************************************************************************
 * Copyright (c) 2013, 2015 IBM Corporation and others 
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
import org.eclipse.orion.server.cf.objects.Space;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetSpaceCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private boolean isGuid;
	private String defaultSpace;
	private String commandName;
	private String space;

	public SetSpaceCommand(Target target, String spaceName) {
		super(target);
		this.space = spaceName;
		this.isGuid = false;
		this.defaultSpace = null;
		this.commandName = "Set Space"; //$NON-NLS-1$
	}

	public SetSpaceCommand(Target target, String space, boolean isGuid) {
		super(target);
		this.space = space;
		this.isGuid = isGuid;
		this.defaultSpace = null;
		this.commandName = "Set Space"; //$NON-NLS-1$
	}

	/**
	 * Defines the default space to choose in case no specific space
	 *  is requested using the {@link SetSpaceCommand} constructor.
	 * @param defaultSpace
	 */
	public void setDefaultSpace(String defaultSpace) {
		this.defaultSpace = defaultSpace;
	}

	/**
	 * Attempts to find the given space.
	 * @param orgs The available non-empty space array
	 * @param orgSpace The space to find
	 * @throws JSONException
	 */
	protected Space getSpace(JSONArray spaces, String orgSpace) throws JSONException {
		for (int i = 0; i < spaces.length(); i++) {
			JSONObject spaceJSON = spaces.getJSONObject(i);
			if ((!isGuid && orgSpace.equals(spaceJSON.getJSONObject("entity").getString("name"))) || (isGuid && orgSpace.equals(spaceJSON.getJSONObject("metadata").getString("guid")))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				return new Space().setCFJSON(spaceJSON);
		}

		return null;
	}

	/**
	 * Returns an arbitrary space.
	 * @param spaces The available non-empty space array
	 * @throws JSONException
	 */
	protected Space getArbitrarySpace(JSONArray spaces) throws JSONException {
		JSONObject org = spaces.getJSONObject(0);
		return new Space().setCFJSON(org);
	}

	public ServerStatus _doIt() {
		try {

			URI targetURI = URIUtil.toURI(target.getUrl());
			String spaceUrl = target.getOrg().getCFJSON().getJSONObject("entity").getString("spaces_url"); //$NON-NLS-1$//$NON-NLS-2$
			URI spaceURI = targetURI.resolve(spaceUrl);

			GetMethod getMethod = new GetMethod(spaceURI.toString());
			HttpUtil.configureHttpMethod(getMethod, target.getCloud());
			ServerStatus getStatus = HttpUtil.executeMethod(getMethod);
			if (!getStatus.isOK())
				return getStatus;

			JSONObject result = getStatus.getJsonData();
			JSONArray spaces = result.getJSONArray("resources"); //$NON-NLS-1$

			if (spaces.length() == 0)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Space not found", null);

			if (space == null || "".equals(space)) { //$NON-NLS-1$

				Space finalFrontier = null;
				if (defaultSpace != null) {
					finalFrontier = getSpace(spaces, defaultSpace);
					if (finalFrontier == null)
						finalFrontier = getArbitrarySpace(spaces);
				} else
					finalFrontier = getArbitrarySpace(spaces);

				target.setSpace(finalFrontier);

			} else {
				Space finalFrontier = getSpace(spaces, space);
				target.setSpace(finalFrontier);
			}

			if (target.getSpace() == null)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Space not found", null);

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, target.getSpace().toJSON());

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
