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
import java.util.Iterator;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.ManifestUtils;
import org.eclipse.orion.server.cf.manifest.ParseException;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BindServicesCommand extends AbstractRevertableCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	public BindServicesCommand(Target target, App app) {
		super(target, app);

		String[] bindings = {app.getName(), app.getGuid()};
		this.commandName = NLS.bind("Bind new services to application {1} (guid: {2})", bindings);
	}

	@Override
	protected ServerStatus _doIt() {
		/* multi server status */
		MultiServerStatus status = new MultiServerStatus();

		try {

			/* bind services */
			URI targetURI = URIUtil.toURI(target.getUrl());
			JSONObject appJSON = ManifestUtils.getApplication(application.getManifest());

			if (appJSON.has(CFProtocolConstants.V2_KEY_SERVICES)) {

				/* fetch all services */
				URI servicesURI = targetURI.resolve("/v2/services"); //$NON-NLS-1$
				GetMethod getServicesMethod = new GetMethod(servicesURI.toString());
				HttpUtil.configureHttpMethod(getServicesMethod, target);
				getServicesMethod.setQueryString("inline-relations-depth=1"); //$NON-NLS-1$

				/* send request */
				ServerStatus jobStatus = HttpUtil.executeMethod(getServicesMethod);
				status.add(jobStatus);
				if (!jobStatus.isOK())
					return revert(status);

				JSONObject resp = jobStatus.getJsonData();
				JSONArray servicesJSON = resp.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);

				JSONObject version2 = appJSON.optJSONObject(CFProtocolConstants.V2_KEY_SERVICES);
				JSONArray version6 = appJSON.optJSONArray(CFProtocolConstants.V2_KEY_SERVICES);
				if (version2 != null) {
					JSONObject services = ManifestUtils.getJSON(appJSON, CFProtocolConstants.V2_KEY_SERVICES, CFProtocolConstants.V2_KEY_APPLICATIONS);
					for (String serviceName : JSONObject.getNames(services)) {
						JSONObject serviceJSON = ManifestUtils.getJSON(services, serviceName, CFProtocolConstants.V2_KEY_SERVICES);

						/* support both 'type' and 'label' field as service name */
						String service = serviceJSON.optString(CFProtocolConstants.V2_KEY_TYPE);
						if (service.isEmpty())
							service = ManifestUtils.getString(serviceJSON, CFProtocolConstants.V2_KEY_LABEL, serviceName);

						String provider = ManifestUtils.getString(serviceJSON, CFProtocolConstants.V2_KEY_PROVIDER, serviceName);
						String plan = ManifestUtils.getString(serviceJSON, CFProtocolConstants.V2_KEY_PLAN, serviceName);

						String servicePlanGUID = findServicePlanGUID(service, provider, plan, servicesJSON);
						if (servicePlanGUID == null) {
							String msg = NLS.bind("Failed to find service {0} with plan {1} in target", service, plan);
							status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
							return revert(status);
						}

						/* create service instance */
						URI serviceInstancesURI = targetURI.resolve("/v2/service_instances"); //$NON-NLS-1$
						PostMethod createServiceMethod = new PostMethod(serviceInstancesURI.toString());
						HttpUtil.configureHttpMethod(createServiceMethod, target);

						/* set request body */
						JSONObject createServiceRequest = new JSONObject();
						createServiceRequest.put(CFProtocolConstants.V2_KEY_SPACE_GUID, target.getSpace().getCFJSON().getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID));
						createServiceRequest.put(CFProtocolConstants.V2_KEY_NAME, serviceName);
						createServiceRequest.put(CFProtocolConstants.V2_KEY_SERVICE_PLAN_GUID, servicePlanGUID);
						createServiceMethod.setRequestEntity(new StringRequestEntity(createServiceRequest.toString(), "application/json", "utf-8")); //$NON-NLS-1$ //$NON-NLS-2$

						/* send request */
						jobStatus = HttpUtil.executeMethod(createServiceMethod);
						status.add(jobStatus);
						if (!jobStatus.isOK())
							return revert(status);

						resp = jobStatus.getJsonData();
						String serviceInstanceGUID = resp.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);

						/* bind service to the application */
						URI serviceBindingsURI = targetURI.resolve("/v2/service_bindings"); //$NON-NLS-1$
						PostMethod bindServiceMethod = new PostMethod(serviceBindingsURI.toString());
						HttpUtil.configureHttpMethod(bindServiceMethod, target);

						/* set request body */
						JSONObject bindServiceRequest = new JSONObject();
						bindServiceRequest.put(CFProtocolConstants.V2_KEY_APP_GUID, application.getGuid());
						bindServiceRequest.put(CFProtocolConstants.V2_KEY_SERVICE_INSTANCE_GUID, serviceInstanceGUID);
						bindServiceMethod.setRequestEntity(new StringRequestEntity(bindServiceRequest.toString(), "application/json", "utf-8")); //$NON-NLS-1$ //$NON-NLS-2$

						/* send request */
						jobStatus = HttpUtil.executeMethod(bindServiceMethod);
						status.add(jobStatus);
						if (!jobStatus.isOK())
							return revert(status);
					}
				}
				if (version6 != null) {
					String spaceGuid = target.getSpace().getGuid();
					URI serviceInstancesURI = targetURI.resolve("/v2/spaces/" + spaceGuid + "/service_instances");

					for (int k = 0; k < version6.length(); k++) {
						JSONObject serviceName = version6.getJSONObject(k);
						Iterator iter = serviceName.keys();
						String nameService = null;
						while (iter.hasNext()) {
							String key = (String) iter.next();
							String value = (String) serviceName.get(key);
							nameService = "name:" + (key != "" ? key + ":" : "") + value;
						}
						NameValuePair[] pa = new NameValuePair[] {new NameValuePair("return_user_provided_service_instance", "true"), new NameValuePair("q", nameService), new NameValuePair("inline-relations-depth", "2")};
						GetMethod getServiceMethod = new GetMethod(serviceInstancesURI.toString());
						getServiceMethod.setQueryString(pa);
						HttpUtil.configureHttpMethod(getServiceMethod, target);
						/* send request */
						jobStatus = HttpUtil.executeMethod(getServiceMethod);
						status.add(jobStatus);
						if (!jobStatus.isOK())
							return revert(status);
						resp = jobStatus.getJsonData();
						String serviceInstanceGUID = null;
						JSONArray respArray = resp.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);
						for (int i = 0; i < respArray.length(); i++) {
							JSONObject o = respArray.optJSONObject(i);
							if (o != null) {
								JSONObject str = o.optJSONObject(CFProtocolConstants.V2_KEY_METADATA);
								if (str != null) {
									serviceInstanceGUID = str.getString(CFProtocolConstants.V2_KEY_GUID);
								}
							}
						}

						if (serviceInstanceGUID == null) {
							status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Service " + nameService + " can not be found", null));
							return revert(status);
						}

						/* bind service to the application */
						URI serviceBindingsURI = targetURI.resolve("/v2/service_bindings"); //$NON-NLS-1$
						PostMethod bindServiceMethod = new PostMethod(serviceBindingsURI.toString());
						HttpUtil.configureHttpMethod(bindServiceMethod, target);

						/* set request body */
						JSONObject bindServiceRequest = new JSONObject();
						bindServiceRequest.put(CFProtocolConstants.V2_KEY_APP_GUID, application.getGuid());
						bindServiceRequest.put(CFProtocolConstants.V2_KEY_SERVICE_INSTANCE_GUID, serviceInstanceGUID);
						bindServiceMethod.setRequestEntity(new StringRequestEntity(bindServiceRequest.toString(), "application/json", "utf-8")); //$NON-NLS-1$ //$NON-NLS-2$

						/* send request */
						jobStatus = HttpUtil.executeMethod(bindServiceMethod);
						status.add(jobStatus);
						if (!jobStatus.isOK())
							return revert(status);
					}
				}
			}

			return status;

		} catch (ParseException e) {
			status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null));
			return revert(status);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return revert(status);
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
