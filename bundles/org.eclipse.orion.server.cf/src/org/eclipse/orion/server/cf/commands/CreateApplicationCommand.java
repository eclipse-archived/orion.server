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
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateApplicationCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App application;

	/* shared parameters */
	private String appName;
	private String appCommand;
	private int appInstances;
	private int appMemory;
	private String buildPack;
	private JSONObject env;

	public CreateApplicationCommand(Target target, App app) {
		super(target);
		this.commandName = "Create a new application";
		this.application = app;
	}

	@Override
	protected ServerStatus _doIt() {
		try {

			/* create cloud foundry application */
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI appsURI = targetURI.resolve("/v2/apps"); //$NON-NLS-1$

			PostMethod createAppMethod = new PostMethod(appsURI.toString());
			HttpUtil.configureHttpMethod(createAppMethod, target);

			/* set request body */
			JSONObject createAppRequst = new JSONObject();
			createAppRequst.put(CFProtocolConstants.V2_KEY_SPACE_GUID, target.getSpace().getCFJSON().getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID));
			createAppRequst.put(CFProtocolConstants.V2_KEY_NAME, appName);
			createAppRequst.put(CFProtocolConstants.V2_KEY_INSTANCES, appInstances);
			createAppRequst.put(CFProtocolConstants.V2_KEY_BUILDPACK, buildPack != null ? buildPack : JSONObject.NULL);
			createAppRequst.put(CFProtocolConstants.V2_KEY_COMMAND, appCommand);
			createAppRequst.put(CFProtocolConstants.V2_KEY_MEMORY, appMemory);
			createAppRequst.put(CFProtocolConstants.V2_KEY_STACK_GUID, JSONObject.NULL);
			if (env != null)
				createAppRequst.put(CFProtocolConstants.V2_KEY_ENVIRONMENT_JSON, env);

			createAppMethod.setRequestEntity(new StringRequestEntity(createAppRequst.toString(), "application/json", "utf-8")); //$NON-NLS-1$ //$NON-NLS-2$

			ServerStatus status = HttpUtil.executeMethod(createAppMethod);
			if (!status.isOK())
				return status;

			/* extract application guid */
			JSONObject appResp = status.getJsonData();
			application.setGuid(appResp.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID));
			application.setName(appName);

			return status;

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	@Override
	protected IStatus validateParams() {
		try {
			/* read deploy parameters */
			ManifestParseTree manifest = application.getManifest();
			ManifestParseTree app = manifest.get(CFProtocolConstants.V2_KEY_APPLICATIONS).get(0);

			if (application.getName() != null)
				appName = application.getName();
			else
				appName = app.get(CFProtocolConstants.V2_KEY_NAME).getValue();

			ManifestParseTree commandNode = app.getOpt(CFProtocolConstants.V2_KEY_COMMAND);
			appCommand = (commandNode != null) ? commandNode.getValue() : ""; //$NON-NLS-1$

			ManifestParseTree instancesNode = app.getOpt(CFProtocolConstants.V2_KEY_INSTANCES);
			appInstances = (instancesNode != null) ? Integer.parseInt(instancesNode.getValue()) : 1;

			/* look for v2 memory property first */
			ManifestParseTree memoryNode = app.getOpt(CFProtocolConstants.V2_KEY_MEMORY);
			if (memoryNode != null)
				appMemory = ManifestUtils.normalizeMemoryMeasure(memoryNode.getValue());
			else {
				memoryNode = app.getOpt(CFProtocolConstants.V6_KEY_MEMORY);
				if (memoryNode != null)
					appMemory = ManifestUtils.normalizeMemoryMeasure(memoryNode.getValue());
				else
					/* default memory value */
					appMemory = 1024;
			}

			ManifestParseTree buildpackNode = app.getOpt(CFProtocolConstants.V2_KEY_BUILDPACK);
			buildPack = (buildpackNode != null) ? buildpackNode.getValue() : null;

			/* look for environment variables */
			ManifestParseTree envNode = manifest.getOpt(CFProtocolConstants.V2_KEY_ENV);
			if (envNode != null) {
				env = new JSONObject();
				for (ManifestParseTree var : envNode.getChildren())
					env.put(var.getLabel(), var.getValue());
			}

			/* look for environment variables in app */
			ManifestParseTree appEnvNode = app.getOpt(CFProtocolConstants.V2_KEY_ENV);
			if (appEnvNode != null) {
				env = new JSONObject();
				for (ManifestParseTree var : appEnvNode.getChildren())
					env.put(var.getLabel(), var.getValue());
			}

			return Status.OK_STATUS;

		} catch (InvalidAccessException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
