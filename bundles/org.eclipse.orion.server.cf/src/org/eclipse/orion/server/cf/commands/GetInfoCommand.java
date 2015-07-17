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
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.CFExtServiceHelper;
import org.eclipse.orion.server.cf.objects.Cloud;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetInfoCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	public GetInfoCommand(Cloud cloud) {
		super(cloud);
		this.commandName = "Get Info";
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			/* get available orgs */
			URI infoURI = URIUtil.toURI(getCloud().getUrl()).resolve("/v2/info");

			GetMethod getInfoMethod = new GetMethod(infoURI.toString());
			ServerStatus confStatus = HttpUtil.configureHttpMethod(getInfoMethod, getCloud());
			/* Getting info when not authenticated should not fail */
			ServerStatus status = HttpUtil.executeMethod(getInfoMethod);
			return status;
		} catch (Exception e) {
			String msg = NLS.bind("An error occurred when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	@Override
	protected ServerStatus retryIfNeeded(ServerStatus doItStatus) {
		CFExtServiceHelper helper = CFExtServiceHelper.getDefault();
		if (!doItStatus.getJsonData().has("user") && helper != null && helper.getService() != null) {
			getCloud().setAccessToken(helper.getService().getToken(getCloud()));
			return _doIt();
		}
		return doItStatus;
	}
}
