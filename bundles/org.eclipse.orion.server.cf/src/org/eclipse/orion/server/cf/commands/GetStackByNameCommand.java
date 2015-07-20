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
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.Stack;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetStackByNameCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String stackName;
	private Stack stack;

	public GetStackByNameCommand(Target target, String stackName) {
		super(target);
		this.commandName = "Get stack by name";
		this.stackName = stackName;
	}

	public Stack getStack() {
		return this.stack;
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI servicesURI = targetURI.resolve("/v2/stacks"); //$NON-NLS-0$//$NON-NLS-1$

			GetMethod getStacksMethod = new GetMethod(servicesURI.toString());
			NameValuePair[] params = new NameValuePair[] { //
			new NameValuePair("q", "name:" + stackName), //$NON-NLS-0$ //$NON-NLS-1$
					new NameValuePair("inline-relations-depth", "1") //$NON-NLS-0$ //$NON-NLS-1$
			};
			getStacksMethod.setQueryString(params);

			ServerStatus confStatus = HttpUtil.configureHttpMethod(getStacksMethod, target.getCloud());
			if (!confStatus.isOK())
				return confStatus;

			ServerStatus getStacksStatus = HttpUtil.executeMethod(getStacksMethod);
			if (!getStacksStatus.isOK())
				return getStacksStatus;
			
			JSONObject stacksJSON = getStacksStatus.getJsonData();
			if (stacksJSON.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1) {
				return getStacksStatus;
			}

			JSONArray resources = stacksJSON.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);
			JSONObject stackJSON = resources.getJSONObject(0);
			stack = new Stack().setCFJSON(stackJSON);

			return getStacksStatus;
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
