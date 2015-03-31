/*******************************************************************************
 * Copyright (c) 2013, 2015 IBM Corporation and others 
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
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
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
	private boolean defaultDomainMode;

	private List<Domain> domains;

	public GetDomainsCommand(Target target) {
		super(target);
		this.commandName = "Get available target domains";
	}

	public GetDomainsCommand(Target target, boolean defaultDomainMode) {
		super(target);
		this.commandName = "Get available target domains";
		this.defaultDomainMode = defaultDomainMode;
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
			JSONObject result = new JSONObject();
			domains = new ArrayList<Domain>();
			URI targetURI = URIUtil.toURI(target.getUrl());
			JSONObject domainsPrivateJSON = null;
			JSONArray resources = null;
			
			if (!defaultDomainMode) {
				// Get private domains
				String privateDomainsURL = target.getOrg().getCFJSON().getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_PRIVATE_DOMAINS_URL);
				URI privateDomainsURI = targetURI.resolve(privateDomainsURL);

				GetMethod getPrivateDomainMethod = new GetMethod(privateDomainsURI.toString());
				HttpUtil.configureHttpMethod(getPrivateDomainMethod, target.getCloud());

				if (domainName != null)
					getPrivateDomainMethod.setQueryString("q=" + URLEncoder.encode(CFProtocolConstants.V2_KEY_NAME + ":" + domainName, ("UTF8")));

				ServerStatus getPrivateDomainsStatus = HttpUtil.executeMethod(getPrivateDomainMethod);
				if (!getPrivateDomainsStatus.isOK())
					return getPrivateDomainsStatus;

				domainsPrivateJSON = getPrivateDomainsStatus.getJsonData();
				resources = domainsPrivateJSON.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);

				for (int k = 0; k < resources.length(); ++k) {
					JSONObject domainJSON = resources.getJSONObject(k);
					Domain domain = new Domain();
					domain.setCFJSON(domainJSON);
					domains.add(domain);
					result.append("Domains", domain.toJSON());
				}				
			}

			if (domainName == null ||
					defaultDomainMode ||
					domainsPrivateJSON.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1) {
				// Get shared domains
				URI sharedDomainsURI = targetURI.resolve("/v2/shared_domains");

				GetMethod getSharedDomainMethod = new GetMethod(sharedDomainsURI.toString());
				HttpUtil.configureHttpMethod(getSharedDomainMethod, target.getCloud());

				if (domainName != null) {
					getSharedDomainMethod.setQueryString("q=" + URLEncoder.encode(CFProtocolConstants.V2_KEY_NAME + ":" + domainName, ("UTF8")));
				} else if (defaultDomainMode) {
					// Return only the first result
					getSharedDomainMethod.setQueryString("page=1&results-per-page=1");
				}
					
				ServerStatus getSharedDomainsStatus = HttpUtil.executeMethod(getSharedDomainMethod);
				if (!getSharedDomainsStatus.isOK())
					return getSharedDomainsStatus;

				JSONObject domainsSharedJSON = getSharedDomainsStatus.getJsonData();
				resources = domainsSharedJSON.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);

				for (int k = 0; k < resources.length(); ++k) {
					JSONObject domainJSON = resources.getJSONObject(k);
					Domain domain = new Domain();
					domain.setCFJSON(domainJSON);
					domains.add(domain);
					result.append("Domains", domain.toJSON());
				}

				if (domains.isEmpty()) {
					return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Domain can not be found", null);
				} else {
					return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
				}
			} else {
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
			}
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
