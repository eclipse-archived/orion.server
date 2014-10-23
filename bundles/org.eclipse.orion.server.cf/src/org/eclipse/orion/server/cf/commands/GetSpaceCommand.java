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
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.objects.Cloud;
import org.eclipse.orion.server.cf.objects.Space;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetSpaceCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	private String spaceId;

	private Space space;

	public GetSpaceCommand(String userId, Cloud cloud, String spaceId) {
		super(cloud);
		this.spaceId = spaceId;
		this.commandName = "Get Space";
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			URI targetURI = URIUtil.toURI(getCloud().getUrl());

			/* get space */
			URI spacesURI = targetURI.resolve("/v2/spaces/" + this.spaceId);

			GetMethod getSpaceMethod = new GetMethod(spacesURI.toString());
			HttpUtil.configureHttpMethod(getSpaceMethod, getCloud());
			getSpaceMethod.setQueryString("inline-relations-depth=1"); //$NON-NLS-1$

			ServerStatus status = HttpUtil.executeMethod(getSpaceMethod);
			if (!status.isOK())
				return status;

			space = new Space().setCFJSON(status.getJsonData());
			JSONObject result = space.toJSON();

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

	public Space getSpace() {
		return space;
	}
}
