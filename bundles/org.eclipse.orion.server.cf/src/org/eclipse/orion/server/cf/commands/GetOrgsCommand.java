/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others 
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.ExpiryCache;
import org.eclipse.orion.server.cf.objects.OrgWithSpaces;
import org.eclipse.orion.server.cf.objects.Space;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetOrgsCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$
	private static final int CACHE_EXPIRES_MS = 300000;
	private static final int MAX_CACHE_SIZE = 1000;

	private String commandName;
	private String userId;
	static ExpiryCache<JSONObject> orgsCache = new ExpiryCache<JSONObject>(MAX_CACHE_SIZE, CACHE_EXPIRES_MS);
	static Set<List<Object>> check = new HashSet<List<Object>>();

	public GetOrgsCommand(String userId, Target target) {
		super(target);
		this.userId = userId;
		this.commandName = "Get Spaces";
	}

	@Override
	protected ServerStatus _doIt() {
		final List<Object> key = Arrays.asList(target, userId);
		final JSONObject cachedOrgs = orgsCache.get(key);
		if (cachedOrgs != null) {
			if (!check.contains(key)) {
				logger.debug(NLS.bind("Need to validate {1} cache for user {0}", userId, target.getUrl().toString()));
				new Job("Validating cache") {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						return getOrgs();
					}
				}.schedule();
			}
			
			logger.debug(NLS.bind("Geting orgs from {1} cache for user {0}", userId, target.getUrl().toString()));
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, cachedOrgs);
		}

		return getOrgs();
	}

	private ServerStatus getOrgs() {
		List<Object> key = Arrays.asList(target, userId);
		try {
			logger.debug(NLS.bind("Validating {1} cache for user {0}", userId, target.getUrl().toString()));
			check.add(key);
			
			/* get available orgs */
			URI targetURI = URIUtil.toURI(target.getUrl());
			URI orgsURI = targetURI.resolve("/v2/organizations");

			GetMethod getOrgsMethod = new GetMethod(orgsURI.toString());
			ServerStatus confStatus = HttpUtil.configureHttpMethod(getOrgsMethod, target.getCloud());
			if (!confStatus.isOK())
				return confStatus;

			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new NameValuePair("inline-relations-depth", "1"));
			if (target.getCloud().getRegion() != null) {
				params.add(new NameValuePair("region", target.getCloud().getRegion()));
			}
			getOrgsMethod.setQueryString(params.toArray(new NameValuePair[params.size()]));

			ServerStatus getOrgsStatus = HttpUtil.executeMethod(getOrgsMethod);
			if (!getOrgsStatus.isOK() && getOrgsStatus.getHttpCode() != HttpServletResponse.SC_PARTIAL_CONTENT)
				return getOrgsStatus;

			/* extract available orgs */
			JSONObject orgs = getOrgsStatus.getJsonData();

			if (orgs == null || orgs.optInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS, 0) < 1) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NO_CONTENT, "Server did not return any organizations.", null);
			}

			/* look if the domain is available */
			JSONObject result = new JSONObject();
			int resources = orgs.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).length();
			for (int k = 0; k < resources; ++k) {
				JSONObject orgJSON = orgs.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).getJSONObject(k);

				List<Space> spaces = new ArrayList<Space>();
				ServerStatus getSpacesStatus = getSpaces(spaces, orgJSON);
				if (!getSpacesStatus.isOK())
					return getSpacesStatus;

				OrgWithSpaces orgWithSpaces = new OrgWithSpaces();
				orgWithSpaces.setCFJSON(orgJSON);
				orgWithSpaces.setSpaces(spaces);
				result.append("Orgs", orgWithSpaces.toJSON());
			}
			
			orgsCache.put(key, result);
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
		} catch (ConnectTimeoutException e) {
			orgsCache.remove(key);
			String msg = NLS.bind("An error occurred when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_GATEWAY_TIMEOUT, msg, e);
		} catch (Exception e) {
			orgsCache.remove(key);
			String msg = NLS.bind("An error occurred when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		} finally {
			check.remove(key);
			logger.debug(NLS.bind("Validation for {1} cache for user {0} finished", userId, target.getUrl().toString()));
		}
	}

	private ServerStatus getSpaces(List<Space> spaces, JSONObject orgJSON) throws Exception {
		long time = System.currentTimeMillis();
		URI targetURI = URIUtil.toURI(target.getUrl());
		URI spaceURI = targetURI.resolve(orgJSON.getJSONObject("entity").getString("spaces_url"));

		GetMethod getDomainsMethod = new GetMethod(spaceURI.toString());
		ServerStatus confStatus = HttpUtil.configureHttpMethod(getDomainsMethod, target.getCloud());
		if (!confStatus.isOK())
			return confStatus;

		getDomainsMethod.setQueryString("inline-relations-depth=1"); //$NON-NLS-1$

		ServerStatus status = HttpUtil.executeMethod(getDomainsMethod);
		if (!status.isOK())
			return status;

		/* extract available spaces */
		JSONObject orgs = status.getJsonData();

		if (orgs.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1) {
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
		}

		/* look if the domain is available */
		int resources = orgs.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).length();
		for (int k = 0; k < resources; ++k) {
			JSONObject spaceJSON = orgs.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES).getJSONObject(k);
			spaces.add(new Space().setCFJSON(spaceJSON));
		}

		logger.debug("GetOrgsCommand: getting spaces using " + spaceURI + " took " + (System.currentTimeMillis() - time));

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}
}
