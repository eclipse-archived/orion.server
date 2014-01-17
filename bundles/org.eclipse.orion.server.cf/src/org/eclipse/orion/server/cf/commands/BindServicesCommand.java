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
import org.apache.commons.httpclient.methods.*;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BindServicesCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private JSONObject manifest;
	private App application;

	public BindServicesCommand(String userId, Target target, App app, JSONObject manifest) {
		super(target, userId);

		String[] bindings = {app.getName(), app.getGuid()};
		this.commandName = NLS.bind("Bind new services to application {1} (guid: {2})", bindings);

		this.manifest = manifest;
		this.application = app;
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			/* bind services */
			URI targetURI = URIUtil.toURI(target.getUrl());
			JSONObject appJSON = manifest.getJSONArray(CFProtocolConstants.V2_KEY_APPLICATIONS).getJSONObject(0);

			if (appJSON.has(CFProtocolConstants.V2_KEY_SERVICES)) {

				/* fetch all services */
				URI servicesURI = targetURI.resolve("/v2/services");
				GetMethod getServicesMethod = new GetMethod(servicesURI.toString());
				HttpUtil.configureHttpMethod(getServicesMethod, target);
				getServicesMethod.setQueryString("inline-relations-depth=1");

				/* send request */
				ServerStatus jobStatus = HttpUtil.executeMethod(getServicesMethod);
				if (!jobStatus.isOK())
					return jobStatus;

				JSONObject resp = jobStatus.getJsonData();
				JSONArray servicesJSON = resp.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);

				JSONObject services = appJSON.getJSONObject(CFProtocolConstants.V2_KEY_SERVICES);
				for (String serviceName : JSONObject.getNames(services)) {

					/* support both 'type' and 'label' field as service name */
					String service = services.getJSONObject(serviceName).optString(CFProtocolConstants.V2_KEY_TYPE);
					if (service.isEmpty())
						service = services.getJSONObject(serviceName).getString(CFProtocolConstants.V2_KEY_LABEL);

					String provider = services.getJSONObject(serviceName).getString(CFProtocolConstants.V2_KEY_PROVIDER);
					String plan = services.getJSONObject(serviceName).getString(CFProtocolConstants.V2_KEY_PLAN);

					String servicePlanGUID = findServicePlanGUID(service, provider, plan, servicesJSON);
					if (servicePlanGUID == null) {
						String msg = NLS.bind("Failed to find service {0} with plan {1} in target", service, plan);
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);
					}

					/* create service instance */
					URI serviceInstancesURI = targetURI.resolve("/v2/service_instances");
					PostMethod createServiceMethod = new PostMethod(serviceInstancesURI.toString());
					HttpUtil.configureHttpMethod(createServiceMethod, target);

					/* set request body */
					JSONObject createServiceRequest = new JSONObject();
					createServiceRequest.put(CFProtocolConstants.V2_KEY_SPACE_GUID, target.getSpace().getCFJSON().getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID));
					createServiceRequest.put(CFProtocolConstants.V2_KEY_NAME, serviceName);
					createServiceRequest.put(CFProtocolConstants.V2_KEY_SERVICE_PLAN_GUID, servicePlanGUID);
					createServiceMethod.setRequestEntity(new StringRequestEntity(createServiceRequest.toString(), "application/json", "utf-8"));

					/* send request */
					jobStatus = HttpUtil.executeMethod(createServiceMethod);
					if (!jobStatus.isOK())
						return jobStatus;

					resp = jobStatus.getJsonData();
					String serviceInstanceGUID = resp.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);

					/* bind service to the application */
					URI serviceBindingsURI = targetURI.resolve("/v2/service_bindings");
					PostMethod bindServiceMethod = new PostMethod(serviceBindingsURI.toString());
					HttpUtil.configureHttpMethod(bindServiceMethod, target);

					/* set request body */
					JSONObject bindServiceRequest = new JSONObject();
					bindServiceRequest.put(CFProtocolConstants.V2_KEY_APP_GUID, application.getGuid());
					bindServiceRequest.put(CFProtocolConstants.V2_KEY_SERVICE_INSTANCE_GUID, serviceInstanceGUID);
					bindServiceMethod.setRequestEntity(new StringRequestEntity(bindServiceRequest.toString(), "application/json", "utf-8"));

					/* send request */
					jobStatus = HttpUtil.executeMethod(bindServiceMethod);
					if (!jobStatus.isOK())
						return jobStatus;
				}
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	/* helper method to find the appropriate service plan guid */
	private String findServicePlanGUID(String service, String provider, String plan, JSONArray servicesJSON) throws JSONException {
		for (int i = 0; i < servicesJSON.length(); ++i) {
			JSONObject serviceJSON = servicesJSON.getJSONObject(i).getJSONObject(CFProtocolConstants.V2_KEY_ENTITY);
			if (service.equals(serviceJSON.getString(CFProtocolConstants.V2_KEY_LABEL)) && provider.equals(serviceJSON.getString(CFProtocolConstants.V2_KEY_PROVIDER))) {

				/* find correct service plan */
				JSONArray servicePlans = serviceJSON.getJSONArray(CFProtocolConstants.V2_KEY_SERVICE_PLANS);
				for (int j = 0; j < servicePlans.length(); ++j) {
					JSONObject servicePlan = servicePlans.getJSONObject(j);
					if (plan.equals(servicePlan.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_NAME)))
						return servicePlan.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);
				}
			}
		}

		return null;
	}
}
