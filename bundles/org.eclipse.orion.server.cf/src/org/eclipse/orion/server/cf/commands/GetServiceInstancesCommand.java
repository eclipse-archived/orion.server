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
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.Service;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetServiceInstancesCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	public GetServiceInstancesCommand(Target target) {
		super(target);
		this.commandName = "Get service instances";
	}

	@Override
	protected ServerStatus _doIt() {

		/* multi server status */
		MultiServerStatus status = new MultiServerStatus();

		try {

			JSONObject response = new JSONObject();
			JSONArray services = new JSONArray();

			/* get services */
			URI targetURI = URIUtil.toURI(target.getUrl());
			String spaceGuid = target.getSpace().getGuid();

			URI serviceInstancesURI = targetURI.resolve("/v2/spaces/" + spaceGuid + "/service_instances"); //$NON-NLS-1$//$NON-NLS-2$

			do {

				GetMethod getServiceInstancesMethod = new GetMethod(serviceInstancesURI.toString());
				HttpUtil.configureHttpMethod(getServiceInstancesMethod, target);

				/* send request */
				ServerStatus jobStatus = HttpUtil.executeMethod(getServiceInstancesMethod);
				status.add(jobStatus);
				if (!jobStatus.isOK())
					return status;

				JSONObject resp = jobStatus.getJsonData();
				if (resp.has(CFProtocolConstants.V2_KEY_NEXT_URL) && !resp.isNull(CFProtocolConstants.V2_KEY_NEXT_URL))
					serviceInstancesURI = targetURI.resolve(resp.getString(CFProtocolConstants.V2_KEY_NEXT_URL));
				else
					serviceInstancesURI = null;

				JSONArray resources = resp.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);
				for (int i = 0; i < resources.length(); ++i) {
					JSONObject serviceObj = resources.getJSONObject(i);
					Service s = new Service(serviceObj.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_NAME));
					services.put(s.toJSON());
				}

			} while (serviceInstancesURI != null);

			response.put(ProtocolConstants.KEY_CHILDREN, services);
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, response);

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return status;
		}

	}
}
