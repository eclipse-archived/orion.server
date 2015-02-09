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
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.Service;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.ProtocolConstants;
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
			NameValuePair[] pa = new NameValuePair[] {//
			new NameValuePair("return_user_provided_service_instances", "true"), //  //$NON-NLS-1$//$NON-NLS-2$
					new NameValuePair("inline-relations-depth", "1") //  //$NON-NLS-1$ //$NON-NLS-2$
			};

			do {

				GetMethod getServiceInstancesMethod = new GetMethod(serviceInstancesURI.toString());
				getServiceInstancesMethod.setQueryString(pa);
				HttpUtil.configureHttpMethod(getServiceInstancesMethod, target.getCloud());

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

					JSONObject serviceInstanceEntity = serviceObj.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY);

					boolean isBindable = true;
					if (serviceInstanceEntity.has(CFProtocolConstants.V2_KEY_SERVICE_PLAN)) {
						JSONObject serviceEntity = serviceInstanceEntity.getJSONObject(CFProtocolConstants.V2_KEY_SERVICE_PLAN)//
								.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY);
						String serviceGuid = serviceEntity.getString(CFProtocolConstants.V2_KEY_SERVICE_GUID);

						GetServiceCommand getServiceCommand = new GetServiceCommand(target, serviceGuid);

						/* get detailed info about the service */
						jobStatus = (ServerStatus) getServiceCommand.doIt(); /* FIXME: unsafe type cast */
						status.add(jobStatus);
						if (!jobStatus.isOK())
							return status;

						JSONObject serviceResp = jobStatus.getJsonData();
						isBindable = serviceResp.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getBoolean(CFProtocolConstants.V2_KEY_BINDABLE);
					}

					if (isBindable) {
						Service s = new Service(serviceInstanceEntity.getString(CFProtocolConstants.V2_KEY_NAME));
						services.put(s.toJSON());
					}
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
