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
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.Domain;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetDomainsCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String domainName;

	private List<Domain> domains;

	public GetDomainsCommand(Target target) {
		super(target);
		this.commandName = "Get available target domains";
	}

	public GetDomainsCommand(Target target, String domainName) {
		super(target);
		this.commandName = "Get available target domains";
		this.domainName = domainName;
	}

	public List<Domain> getDomains() {
		assertWasRun();
		return domains;
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			/* get available domains */
			URI targetURI = URIUtil.toURI(target.getUrl());
			String domainsURL = target.getSpace().getCFJSON().getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_DOMAINS_URL);
			URI domainsURI = targetURI.resolve(domainsURL);

			GetMethod getDomainsMethod = new GetMethod(domainsURI.toString());
			HttpUtil.configureHttpMethod(getDomainsMethod, target.getCloud());
			getDomainsMethod.setQueryString("inline-relations-depth=1"); //$NON-NLS-1$
			ServerStatus getDomainStatus = HttpUtil.executeMethod(getDomainsMethod);

			/* extract available domains */
			JSONObject domainsJSON = getDomainStatus.getJsonData();

			if (domainsJSON.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1) {
				return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null);
			}

			JSONObject result = new JSONObject();
			domains = new ArrayList<Domain>();
			JSONArray resources = domainsJSON.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);
			for (int k = 0; k < resources.length(); ++k) {
				JSONObject domainJSON = resources.getJSONObject(k);
				Domain domain = new Domain();
				domain.setCFJSON(domainJSON);
				if (domainName == null || domainName.equals(domain.getDomainName())) {
					domains.add(domain);
					result.append("Domains", domain.toJSON());
				}
			}

			if (domains.isEmpty())
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Domain can not be found", null);

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
