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
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetServiceCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String serviceGuid;
	private String commandName;

	public GetServiceCommand(Target target, String serviceGuid) {
		super(target);
		this.serviceGuid = serviceGuid;
		this.commandName = "Get service info";
	}

	@Override
	protected ServerStatus _doIt() {
		try {

			URI targetURI = URIUtil.toURI(target.getUrl());
			URI serviceInstanceURI = targetURI.resolve("/v2/services/" + serviceGuid); //$NON-NLS-1$//$NON-NLS-2$

			GetMethod getServiceMethod = new GetMethod(serviceInstanceURI.toString());
			HttpUtil.configureHttpMethod(getServiceMethod, target.getCloud());

			ServerStatus getStatus = HttpUtil.executeMethod(getServiceMethod);
			if (!getStatus.isOK())
				return getStatus;

			JSONObject service = getStatus.getJsonData();
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, service);

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

}
