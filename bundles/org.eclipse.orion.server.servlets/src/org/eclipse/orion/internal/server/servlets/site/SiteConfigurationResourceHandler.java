package org.eclipse.orion.internal.server.servlets.site;

import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.hosting.*;
import org.eclipse.orion.internal.server.servlets.workspace.WebElementResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.*;

/**
 * Processes an HTTP request for a site configuration resource.
 */
public class SiteConfigurationResourceHandler extends WebElementResourceHandler<SiteConfiguration> {

	private final ServletResourceHandler<IStatus> statusHandler;

	public SiteConfigurationResourceHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	/**
	 * Creates a new site configuration with the given name, a generated id, and the rest of its 
	 * properties given by <code>object</code>. 
	 * @param user User creating the SiteConfiguration
	 * @param name Name for the SiteConfiguration
	 * @param workspace Workspace the SiteConfiguration is associated to
	 * @param object Object from which other properties will be drawn
	 * @return The created SiteConfiguration.
	 */
	public static SiteConfiguration createFromJSON(WebUser user, String name, String workspace, JSONObject object) throws CoreException {
		if (name == null || name.length() == 0)
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Name is missing", null));
		else if (workspace == null || name.length() == 0)
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Workspace is missing", null));

		SiteConfiguration site = user.createSiteConfiguration(name, workspace);
		copyProperties(object, site, false);
		site.save();
		return site;
	}

	/**
	 * Copies properties from a JSONObject representation of a site configuration to a SiteConfiguration
	 * instance.
	 * @param source JSON object to copy from.
	 * @param target Site configuration instance to copy to.
	 * @param copyName If <code>true</code>, the name property from <code>source</code> will overwrite
	 * <code>target</code>'s name.
	 * @throws CoreException if, after copying, <code>target</code> is missing a required property.
	 */
	private static void copyProperties(JSONObject source, SiteConfiguration target, boolean copyName) throws CoreException {
		if (copyName) {
			String name = source.optString(ProtocolConstants.KEY_NAME, null);
			if (name != null)
				target.setName(name);
		}

		String hostHint = source.optString(SiteConfigurationConstants.KEY_HOST_HINT, null);
		if (hostHint != null)
			target.setHostHint(hostHint);

		String workspace = source.optString(SiteConfigurationConstants.KEY_WORKSPACE, null);
		if (workspace != null)
			target.setWorkspace(workspace);

		JSONArray mappings = source.optJSONArray(SiteConfigurationConstants.KEY_MAPPINGS);
		if (mappings != null)
			target.setMappings(mappings);

		// Sanity check
		if (target.getName() == null || target.getName().length() == 0)
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Name was not specified", null));
		if (target.getWorkspace() == null || target.getWorkspace().length() == 0)
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Workspace was not specified", null));
	}

	/**
	 * @param baseLocation The URI of the SiteConfigurationServlet.
	 * @return Representation of <code>site</code> as a JSONObject.
	 */
	public static JSONObject toJSON(SiteConfiguration site, URI baseLocation) {
		JSONObject result = WebElementResourceHandler.toJSON(site);
		try {
			result.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(baseLocation, site.getId()).toString());
			result.putOpt(SiteConfigurationConstants.KEY_HOST_HINT, site.getHostHint());
			result.putOpt(SiteConfigurationConstants.KEY_WORKSPACE, site.getWorkspace());
			result.put(SiteConfigurationConstants.KEY_MAPPINGS, site.getMappingsJSON());

			// Note: The SiteConfigurationConstants.KEY_HOSTING_STATUS field will be contributed to the result
			// by the site-hosting bundle (if present) via an IWebResourceDecorator
		} catch (JSONException e) {
			// Can't happen
		}
		return result;
	}

	private boolean handleGet(HttpServletRequest req, HttpServletResponse resp, SiteConfiguration site) throws IOException {
		// Strip off the SiteConfig id from the request URI
		URI location = getURI(req);
		URI baseLocation = location.resolve(""); //$NON-NLS-1$

		JSONObject result = toJSON(site, baseLocation);
		OrionServlet.writeJSONResponse(req, resp, result);
		return true;
	}

	/**
	 * Creates a new site configuration, and possibly starts it.
	 * @param site <code>null</code>
	 */
	private boolean handlePost(HttpServletRequest req, HttpServletResponse resp, SiteConfiguration site) throws CoreException, IOException, JSONException {
		if (site != null)
			throw new IllegalArgumentException("Can't POST to an existing site");
		String pathInfo = req.getPathInfo();
		IPath path = new Path(pathInfo == null ? "" : pathInfo); //$NON-NLS-1$

		JSONObject requestJson = getRequestJson(req);
		try {
			site = doCreateSiteConfiguration(req, requestJson);
			changeHostingStatus(req, resp, requestJson, site);
		} catch (CoreException e) {
			// If starting it failed, try to clean up
			if (site != null) {
				// Remove site config from user's list
				WebUser user = WebUser.fromUserName(getUserName(req));
				user.removeSiteConfiguration(site);

				// Also delete the site configuration itself since it can never be used
				try {
					site.delete();
				} catch (CoreException deleteException) {
					// Ignore
				}
			}
			throw e;
		}

		URI baseLocation = getURI(req);
		JSONObject result = toJSON(site, baseLocation);
		OrionServlet.writeJSONResponse(req, resp, result);

		resp.setStatus(HttpServletResponse.SC_CREATED);
		resp.addHeader(ProtocolConstants.HEADER_LOCATION, result.getString(ProtocolConstants.KEY_LOCATION));
		return true;
	}

	private boolean handlePut(HttpServletRequest req, HttpServletResponse resp, SiteConfiguration site) throws IOException, CoreException, JSONException {
		JSONObject requestJson = OrionServlet.readJSONRequest(req);
		copyProperties(requestJson, site, true);

		// Start/stop the site if necessary
		changeHostingStatus(req, resp, requestJson, site);

		// Everything succeeded, save the changed site
		site.save();

		// Strip off the SiteConfig id from the request URI
		URI location = getURI(req);
		URI baseLocation = location.resolve(""); //$NON-NLS-1$

		JSONObject result = toJSON(site, baseLocation);
		OrionServlet.writeJSONResponse(req, resp, result);
		return true;
	}

	private boolean handleDelete(HttpServletRequest req, HttpServletResponse resp, SiteConfiguration site) throws CoreException {
		WebUser user = WebUser.fromUserName(req.getRemoteUser());
		ISiteHostingService hostingService = Activator.getDefault().getSiteHostingService();
		IHostedSite runningSite = (IHostedSite) hostingService.get(site, user);
		if (runningSite != null) {
			String msg = NLS.bind("Site configuration is running at {0}. Must be stopped before it can be deleted", runningSite.getHost());
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_CONFLICT, msg, null));
		} else {
			user.removeSiteConfiguration(site);
		}
		return true;
	}

	@Override
	public boolean handleRequest(HttpServletRequest req, HttpServletResponse resp, SiteConfiguration site) throws ServletException {
		if (site == null && getMethod(req) != Method.POST) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Site configuration not found", null)); //$NON-NLS-1$
		}
		try {
			switch (getMethod(req)) {
				case GET :
					return handleGet(req, resp, site);
				case PUT :
					return handlePut(req, resp, site);
				case POST :
					return handlePost(req, resp, site);
				case DELETE :
					return handleDelete(req, resp, site);
			}
		} catch (IOException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		} catch (JSONException e) {
			return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request", e));
		} catch (CoreException e) {
			return statusHandler.handleRequest(req, resp, e.getStatus());
		}
		return false;
	}

	private static JSONObject getRequestJson(HttpServletRequest req) throws IOException, JSONException {
		WebUser user = WebUser.fromUserName(getUserName(req));
		return OrionServlet.readJSONRequest(req);
	}

	/**
	 * Creates a new site configuration from the request
	 */
	private SiteConfiguration doCreateSiteConfiguration(HttpServletRequest req, JSONObject requestJson) throws CoreException {
		WebUser user = WebUser.fromUserName(getUserName(req));
		String name = computeName(req, requestJson);
		String workspace = requestJson.optString(SiteConfigurationConstants.KEY_WORKSPACE, null);
		SiteConfiguration site = SiteConfigurationResourceHandler.createFromJSON(user, name, workspace, requestJson);
		return site;
	}

	/**
	 * Changes <code>site</code>'s hosting status to the desired status. The desired status 
	 * is given by the <code>HostingStatus.Status</code> field of the <code>requestJson</code>.
	 * @param site The site configuration to act on.
	 * @param requestJson The request body, which may or may not have a HostingStatus field.
	 * @throws CoreException If a bogus value was found for <code>HostingStatus.Status</code>,
	 * or if the hosting service threw an exception when trying to change the status.
	 */
	private void changeHostingStatus(HttpServletRequest req, HttpServletResponse resp, JSONObject requestJson, SiteConfiguration site) throws CoreException {
		WebUser user = WebUser.fromUserName(getUserName(req));
		JSONObject hostingStatus = requestJson.optJSONObject(SiteConfigurationConstants.KEY_HOSTING_STATUS);
		if (hostingStatus == null)
			return;
		String status = hostingStatus.optString(SiteConfigurationConstants.KEY_HOSTING_STATUS_STATUS);
		try {
			if ("started".equalsIgnoreCase(status)) { //$NON-NLS-1$
				ISiteHostingService service = getHostingService();
				String editServer = "http://" + req.getHeader("Host"); //$NON-NLS-1$ //$NON-NLS-2$
				service.start(site, user, editServer);
			} else if ("stopped".equalsIgnoreCase(status)) { //$NON-NLS-1$
				ISiteHostingService service = getHostingService();
				service.stop(site, user);
			} else if (status == null) {
				// No Status; ignore it
			} else {
				// Status has a bogus value
				throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Status not understood: {0}", status), null));
			}
		} catch (NoMoreHostsException e) {
			// Give a JSON response object instead of stack trace
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		}
	}

	/**
	 * @throws CoreException If the site hosting service is not present.
	 */
	private static ISiteHostingService getHostingService() throws CoreException {
		ISiteHostingService service = Activator.getDefault().getSiteHostingService();
		if (service == null) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Site hosting service unavailable"));
		}
		return service;
	}

	/**
	 * Computes the name for the resource to be created by a POST operation.
	 * @return The name, or the empty string if no name was given.
	 */
	private static String computeName(HttpServletRequest req, JSONObject requestBody) {
		// Try Slug first
		String name = req.getHeader(ProtocolConstants.HEADER_SLUG);
		if (name == null || name.length() == 0) {
			name = requestBody.optString(ProtocolConstants.KEY_NAME);
		}
		return name;
	}

	/**
	 * Obtain and return the user name from the request headers.
	 */
	private static String getUserName(HttpServletRequest req) {
		return req.getRemoteUser();
	}
}
