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
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetIsRouteAvailableCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String host;
	private String guid;

	public GetIsRouteAvailableCommand(Target target, String host, String guid) {
		super(target);
		this.commandName = "Is Route Available Command";
		this.host = host;
		this.guid = guid;
	}

	@Override
	protected ServerStatus _doIt() {
		try {

			// CF API
			// http://apidocs.cloudfoundry.org/195/routes/check_a_route_exists.html
			// GET /v2/routes/reserved/domain/:domain_guid/host/:host
			// e.g.
			// GET /v2/routes/reserved/domain/c11da0e2-517a-4d53-98a0-5d340fee4b78/host/sample-java-cloudant

			URI targetURI = URIUtil.toURI(getCloud().getUrl());
			String path = "/v2/routes/reserved/domain/" + this.guid + "/host/" + this.host;
			URI requestURI = targetURI.resolve(path);

			GetMethod getIsRouteAvailableMethod = new GetMethod(requestURI.toString());
			HttpUtil.configureHttpMethod(getIsRouteAvailableMethod, target.getCloud());

			return HttpUtil.executeMethod(getIsRouteAvailableMethod);

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
