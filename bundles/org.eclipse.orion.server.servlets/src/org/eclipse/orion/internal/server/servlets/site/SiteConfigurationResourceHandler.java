package org.eclipse.orion.internal.server.servlets.site;

import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.workspace.WebElementResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;
import org.eclipse.orion.server.servlets.OrionServlet;
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
	 * @param object Object from which other properties will be drawn
	 * @return The created SiteConfiguration.
	 */
	public static SiteConfiguration createFromJSON(WebUser user, String name, JSONObject object) throws CoreException {
		SiteConfiguration siteConfig = user.createSiteConfiguration(name);
		copyProperties(object, siteConfig, false);
		siteConfig.save();
		return siteConfig;
	}

	/**
	 * @param copyName If <code>true</code>, the Name property from source will overwrite target's name
	 */
	private static void copyProperties(JSONObject source, SiteConfiguration target, boolean copyName) {
		if (copyName) {
			String name = source.optString(ProtocolConstants.KEY_NAME, null);
			if (name != null)
				target.setName(name);
		}

		String authName = source.optString(SiteConfigurationConstants.KEY_AUTH_NAME, null);
		if (authName != null)
			target.setAuthName(authName);

		String authPassword = source.optString(SiteConfigurationConstants.KEY_AUTH_PASSWORD, null);
		if (authPassword != null)
			target.setAuthPassword(authPassword);

		String hostDomain = source.optString(SiteConfigurationConstants.KEY_HOST_DOMAIN, null);
		if (hostDomain != null)
			target.setHostDomain(hostDomain);

		JSONArray mappings = source.optJSONArray(SiteConfigurationConstants.KEY_MAPPINGS);
		if (mappings != null)
			target.setMappings(mappings);
	}

	/**
	 * @param baseLocation The URI of the SiteConfigurationServlet. If null, Location will not 
	 * be included in the JSON object. 
	 * @return Representation of <code>siteConfig</code> as a JSONObject.
	 */
	public static JSONObject toJSON(SiteConfiguration siteConfig, URI baseLocation) {
		JSONObject result = WebElementResourceHandler.toJSON(siteConfig);
		try {
			result.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(baseLocation, siteConfig.getId()).toString());
			result.put(SiteConfigurationConstants.KEY_MAPPINGS, siteConfig.getMappingsJSON());
			result.putOpt(SiteConfigurationConstants.KEY_AUTH_NAME, siteConfig.getAuthName());
			result.putOpt(SiteConfigurationConstants.KEY_AUTH_PASSWORD, siteConfig.getAuthPassword());
			result.putOpt(SiteConfigurationConstants.KEY_HOST_DOMAIN, siteConfig.getHostDomain());

			// FIXME: include the status of the request
		} catch (JSONException e) {
			// Can't happen
		}
		return result;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, SiteConfiguration siteConfig) throws IOException {
		// Strip off the SiteConfig id from the request URI
		URI location = getURI(request);
		URI baseLocation = location.resolve(""); //$NON-NLS-1$

		JSONObject result = toJSON(siteConfig, baseLocation);
		OrionServlet.writeJSONResponse(request, response, result);
		return true;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, SiteConfiguration siteConfig) throws IOException {
		String pathInfo = request.getPathInfo();
		IPath path = new Path(pathInfo == null ? "" : pathInfo); //$NON-NLS-1$
		boolean created = (path.segmentCount() == 0);

		URI location = getURI(request);
		if (!created) {
			// Strip off the last segment (site config id)
			location = location.resolve(""); //$NON-NLS-1$
		}
		JSONObject result = toJSON(siteConfig, location);
		OrionServlet.writeJSONResponse(request, response, result);

		if (created) {
			response.setStatus(HttpServletResponse.SC_CREATED);
			response.addHeader(ProtocolConstants.HEADER_LOCATION, location.toString());
		}
		return true;
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, SiteConfiguration siteConfig) throws CoreException {
		try {
			JSONObject requestJson = OrionServlet.readJSONRequest(request);
			copyProperties(requestJson, siteConfig, true);
			siteConfig.save();

			// Strip off the SiteConfig id from the request URI
			URI location = getURI(request);
			URI baseLocation = location.resolve(""); //$NON-NLS-1$

			JSONObject result = toJSON(siteConfig, baseLocation);
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} catch (IOException e) {
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while reading the request", e));
		} catch (JSONException e) {
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request body", e));
		}
	}

	private boolean handleDelete(HttpServletRequest request, HttpServletResponse response, SiteConfiguration siteConfig) throws CoreException {
		WebUser user = WebUser.fromUserName(request.getRemoteUser());
		user.deleteSiteConfiguration(siteConfig);
		return true;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, SiteConfiguration siteConfig) throws ServletException {
		if (siteConfig == null) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Site configuration not found", null)); //$NON-NLS-1$
		}
		try {
			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, siteConfig);
				case PUT :
					return handlePut(request, response, siteConfig);
				case POST :
					return handlePost(request, response, siteConfig);
				case DELETE :
					return handleDelete(request, response, siteConfig);
			}
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		} catch (CoreException e) {
			return statusHandler.handleRequest(request, response, e.getStatus());
		}
		return false;
	}
}
