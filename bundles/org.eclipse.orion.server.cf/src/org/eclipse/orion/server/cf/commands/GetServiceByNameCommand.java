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

import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetServiceByNameCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String serviceName;

	public GetServiceByNameCommand(Target target, String serviceName) {
		super(target);
		this.commandName = "Get service by name";
		this.serviceName = serviceName;
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI servicesURI = targetURI.resolve("/v2/spaces/" + target.getSpace().getGuid() + "/services"); //$NON-NLS-0$//$NON-NLS-1$

			GetMethod getServicesMethod = new GetMethod(servicesURI.toString());
			NameValuePair[] params = new NameValuePair[] { //
			new NameValuePair("q", "label:" + serviceName), //$NON-NLS-0$ //$NON-NLS-1$
					new NameValuePair("inline-relations-depth", "1") //$NON-NLS-0$ //$NON-NLS-1$
			};
			getServicesMethod.setQueryString(params);

			ServerStatus confStatus = HttpUtil.configureHttpMethod(getServicesMethod, target.getCloud());
			if (!confStatus.isOK())
				return confStatus;
			
			return HttpUtil.executeMethod(getServicesMethod);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
