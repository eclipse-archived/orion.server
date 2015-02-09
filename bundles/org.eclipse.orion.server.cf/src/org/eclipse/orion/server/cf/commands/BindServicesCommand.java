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
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BindServicesCommand extends AbstractCFApplicationCommand {
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

			ManifestParseTree manifest = getApplication().getManifest();
			ManifestParseTree app = manifest.get("applications").get(0); //$NON-NLS-1$

			if (app.has(CFProtocolConstants.V2_KEY_SERVICES)) {

				/* fetch all services */
				URI servicesURI = targetURI.resolve("/v2/services"); //$NON-NLS-1$
				GetMethod getServicesMethod = new GetMethod(servicesURI.toString());
				HttpUtil.configureHttpMethod(getServicesMethod, target.getCloud());
				getServicesMethod.setQueryString("inline-relations-depth=1"); //$NON-NLS-1$

				/* send request */
				ServerStatus jobStatus = HttpUtil.executeMethod(getServicesMethod);
				status.add(jobStatus);
				if (!jobStatus.isOK())
					return status;

				JSONObject resp = jobStatus.getJsonData();
				JSONArray servicesJSON = resp.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);

				/* check for manifest version */
				ManifestParseTree services = app.getOpt(CFProtocolConstants.V2_KEY_SERVICES);
				if (services == null)
					/* nothing to do */
					return status;

				int version = services.isList() ? 6 : 2;

				if (version == 2) {
					String spaceGuid = target.getSpace().getGuid();
					URI serviceInstancesURI2 = targetURI.resolve("/v2/spaces/" + spaceGuid + "/service_instances"); //$NON-NLS-1$//$NON-NLS-2$

					for (ManifestParseTree service : services.getChildren()) {
						String serviceName = service.getLabel();

						String nameService = "name:" + serviceName; //$NON-NLS-1$
						NameValuePair[] pa = new NameValuePair[] {new NameValuePair("return_user_provided_service_instances", "true"), //  //$NON-NLS-1$//$NON-NLS-2$
								new NameValuePair("q", nameService), new NameValuePair("inline-relations-depth", "1") //  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						};

						GetMethod getServiceMethod = new GetMethod(serviceInstancesURI2.toString());
						getServiceMethod.setQueryString(pa);
						HttpUtil.configureHttpMethod(getServiceMethod, target.getCloud());

						/* send request */
						jobStatus = HttpUtil.executeMethod(getServiceMethod);
						status.add(jobStatus);
						if (!jobStatus.isOK())
							return status;

						resp = jobStatus.getJsonData();
						String serviceInstanceGUID = null;
						JSONArray respArray = resp.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);
						for (int i = 0; i < respArray.length(); ++i) {
							JSONObject o = respArray.optJSONObject(i);
							if (o != null) {
								JSONObject str = o.optJSONObject(CFProtocolConstants.V2_KEY_METADATA);
								if (str != null) {
									serviceInstanceGUID = str.getString(CFProtocolConstants.V2_KEY_GUID);
									break;
								}
							}
						}

						if (serviceInstanceGUID == null) {
							/* no service instance bound to the application, create one if possible */

							/* support both 'type' and 'label' fields as service type */
							ManifestParseTree serviceType = service.getOpt(CFProtocolConstants.V2_KEY_TYPE);
							if (serviceType == null)
								serviceType = service.get(CFProtocolConstants.V2_KEY_LABEL);

							ManifestParseTree provider = service.get(CFProtocolConstants.V2_KEY_PROVIDER);
							ManifestParseTree plan = service.get(CFProtocolConstants.V2_KEY_PLAN);

							String servicePlanGUID = findServicePlanGUID(serviceType.getValue(), provider.getValue(), plan.getValue(), servicesJSON);
							if (servicePlanGUID == null) {
								String[] bindings = {serviceName, serviceType.getValue(), plan.getValue()};
								String msg = NLS.bind("Could not find service instance {0} nor service {1} with plan {2} in target.", bindings);
								status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
								return status;
							}

							/* create service instance */
							URI serviceInstancesURI = targetURI.resolve("/v2/service_instances"); //$NON-NLS-1$
							PostMethod createServiceMethod = new PostMethod(serviceInstancesURI.toString());
							HttpUtil.configureHttpMethod(createServiceMethod, target.getCloud());

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
								return status;

							resp = jobStatus.getJsonData();
							serviceInstanceGUID = resp.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID);
						}

						/* bind service to the application */
						URI serviceBindingsURI = targetURI.resolve("/v2/service_bindings"); //$NON-NLS-1$
						PostMethod bindServiceMethod = new PostMethod(serviceBindingsURI.toString());
						HttpUtil.configureHttpMethod(bindServiceMethod, target.getCloud());

						/* set request body */
						JSONObject bindServiceRequest = new JSONObject();
						bindServiceRequest.put(CFProtocolConstants.V2_KEY_APP_GUID, getApplication().getGuid());
						bindServiceRequest.put(CFProtocolConstants.V2_KEY_SERVICE_INSTANCE_GUID, serviceInstanceGUID);
						bindServiceMethod.setRequestEntity(new StringRequestEntity(bindServiceRequest.toString(), "application/json", "utf-8")); //$NON-NLS-1$ //$NON-NLS-2$

						/* send request */
						jobStatus = HttpUtil.executeMethod(bindServiceMethod);
						status.add(jobStatus);
						if (!jobStatus.isOK())
							return status;
					}
				}

				if (version == 6) {

					String spaceGuid = target.getSpace().getGuid();
					URI serviceInstancesURI = targetURI.resolve("/v2/spaces/" + spaceGuid + "/service_instances"); //$NON-NLS-1$//$NON-NLS-2$

					for (ManifestParseTree service : services.getChildren()) {
						String nameService = service.getValue();

						NameValuePair[] pa = new NameValuePair[] {new NameValuePair("return_user_provided_service_instances", "true"), // //$NON-NLS-1$ //$NON-NLS-2$
								new NameValuePair("q", "name:" + nameService), new NameValuePair("inline-relations-depth", "1")}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

						GetMethod getServiceMethod = new GetMethod(serviceInstancesURI.toString());
						getServiceMethod.setQueryString(pa);
						HttpUtil.configureHttpMethod(getServiceMethod, target.getCloud());

						/* send request */
						jobStatus = HttpUtil.executeMethod(getServiceMethod);
						status.add(jobStatus);
						if (!jobStatus.isOK())
							return status;

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
							status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Service instance " + nameService + " can not be found in target space", null));
							return status;
						}

						/* bind service to the application */
						URI serviceBindingsURI = targetURI.resolve("/v2/service_bindings"); //$NON-NLS-1$
						PostMethod bindServiceMethod = new PostMethod(serviceBindingsURI.toString());
						HttpUtil.configureHttpMethod(bindServiceMethod, target.getCloud());

						/* set request body */
						JSONObject bindServiceRequest = new JSONObject();
						bindServiceRequest.put(CFProtocolConstants.V2_KEY_APP_GUID, getApplication().getGuid());
						bindServiceRequest.put(CFProtocolConstants.V2_KEY_SERVICE_INSTANCE_GUID, serviceInstanceGUID);
						bindServiceMethod.setRequestEntity(new StringRequestEntity(bindServiceRequest.toString(), "application/json", "utf-8")); //$NON-NLS-1$ //$NON-NLS-2$

						/* send request */
						jobStatus = HttpUtil.executeMethod(bindServiceMethod);

						if (!jobStatus.isOK()) {

							/* the binding might be already present - detect it by checking the error code type */
							if (!jobStatus.getJsonData().has("error_code") || !"CF-ServiceBindingAppServiceTaken".equals(jobStatus.getJsonData().getString("error_code"))) { //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
								status.add(jobStatus);
								return status;
							}
						} else
							status.add(jobStatus);
					}
				}
			}

			return status;

		} catch (InvalidAccessException e) {
			status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null));
			return status;
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return status;
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
