package org.eclipse.orion.internal.server.servlets.site;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.*;

/**
 * Servlet for managing site configurations.
 */
public class SiteConfigurationServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	private ServletResourceHandler<SiteConfiguration> siteConfigurationResourceHandler;

	public SiteConfigurationServlet() {
		siteConfigurationResourceHandler = new SiteConfigurationResourceHandler(getStatusHandler());
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 0) {
			doGetAllSiteConfigurations(req, resp);
			return;
		} else if (pathInfo.segmentCount() == 1) {
			SiteConfiguration siteConfig = getExistingSiteConfig(req, resp);
			if (siteConfigurationResourceHandler.handleRequest(req, resp, siteConfig)) {
				return;
			}
		} else {
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request", null));
			return;
		}
		super.doGet(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		IPath pathInfo = getPathInfo(req);
		try {
			if (pathInfo.segmentCount() == 0) {
				// Create a new site configuration, and possibly start it
				SiteConfiguration siteConfig = doCreateSiteConfiguration(req, resp);
				try {
					doStartStop(req, resp, siteConfig, false);
				} catch (CoreException e) {
					// Start/stop failed; undo creation of site configuration
					if (siteConfig != null) {
						WebUser user = WebUser.fromUserName(getUserName(req));
						user.removeSiteConfiguration(siteConfig);
					}
					throw e;
				}
				if (siteConfigurationResourceHandler.handleRequest(req, resp, siteConfig)) {
					return;
				}
			} else if (pathInfo.segmentCount() == 1) {
				// Start/stop site configuration
				SiteConfiguration siteConfig = getExistingSiteConfig(req, resp);
				doStartStop(req, resp, siteConfig, true);
				if (siteConfigurationResourceHandler.handleRequest(req, resp, siteConfig)) {
					return;
				}
			} else {
				// Too many segments in request URI
				handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request", null));
				return;
			}
		} catch (CoreException e) {
			handleException(resp, e.getStatus());
			return;
		}
		super.doPost(req, resp);
	}

	/**
	 * @param siteConfig
	 * @param actionRequired <code>true</code> if null action header should cause a failure response.
	 */
	private void doStartStop(HttpServletRequest req, HttpServletResponse resp, SiteConfiguration siteConfig, boolean actionRequired) throws CoreException {
		if (siteConfig == null)
			return;
		String action = req.getHeader(SiteConfigurationConstants.HEADER_ACTION);
		if ("start".equalsIgnoreCase(action)) { //$NON-NLS-1$
			ISiteHostingService service = getHostingService();
			service.start(siteConfig);
		} else if ("stop".equalsIgnoreCase(action)) { //$NON-NLS-1$
			ISiteHostingService service = getHostingService();
			service.stop(siteConfig);
		} else if (action == null) {
			if (actionRequired)
				throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Action missing", null));
			else
				return;
		} else {
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Action not understood", null));
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

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 1) {
			// Update site configuration
			SiteConfiguration siteConfig = getExistingSiteConfig(req, resp);
			if (siteConfigurationResourceHandler.handleRequest(req, resp, siteConfig)) {
				return;
			}
		} else {
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request", null));
			return;
		}
		super.doPut(req, resp);
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		if (getPathInfo(req).segmentCount() == 1) {
			// Delete site configuration
			SiteConfiguration siteConfig = getExistingSiteConfig(req, resp);
			if (siteConfigurationResourceHandler.handleRequest(req, resp, siteConfig)) {
				return;
			}
		} else {
			handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Bad request", null));
		}
		super.doDelete(req, resp);
	}

	/**
	 * @return The SiteConfiguration whose id matches the 0th segment of the request pathInfo, or null.
	 */
	private SiteConfiguration getExistingSiteConfig(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String userName = getUserName(req);
		WebUser user = WebUser.fromUserName(userName);
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 1) {
			return user.getSiteConfiguration(pathInfo.segment(0));
		}
		return null;
	}

	// FIXME: implement filtering by state
	private boolean doGetAllSiteConfigurations(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String userName = getUserName(req);
		try {
			WebUser user = WebUser.fromUserName(userName);
			JSONArray siteConfigurations = user.getSiteConfigurationsJSON(ServletResourceHandler.getURI(req));
			JSONObject jsonResponse = new JSONObject();
			jsonResponse.put(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS, siteConfigurations);
			writeJSONResponse(req, resp, jsonResponse);
		} catch (Exception e) {
			handleException(resp, "An error occurred while obtaining site configurations", e);
		}
		return true;
	}

	/**
	 * Creates a new site configuration from the request
	 */
	private SiteConfiguration doCreateSiteConfiguration(HttpServletRequest req, HttpServletResponse resp) throws CoreException {
		try {
			WebUser user = WebUser.fromUserName(getUserName(req));
			JSONObject requestJson = readJSONRequest(req);
			String name = computeName(req, requestJson);
			if (name.isEmpty()) {
				throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Site configuration name was not specified", null));
			}
			SiteConfiguration siteConfig = SiteConfigurationResourceHandler.createFromJSON(user, name, requestJson);
			return siteConfig;
		} catch (IOException e) {
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while reading the request", null));
		} catch (JSONException e) {
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request body", null));
		}
	}

	/**
	 * Computes the name for the resource to be created by a POST operation.
	 * @return The name, or the empty string if no name was given.
	 */
	private static String computeName(HttpServletRequest req, JSONObject requestBody) {
		// Try Slug first
		String name = req.getHeader(ProtocolConstants.HEADER_SLUG);
		if (name == null || name.isEmpty()) {
			name = requestBody.optString(ProtocolConstants.KEY_NAME);
		}
		return name;
	}

	/**
	 * @return The request's PathInfo as an IPath.
	 */
	private static IPath getPathInfo(HttpServletRequest req) {
		String pathString = req.getPathInfo();
		if (pathString == null) {
			return new Path(""); //$NON-NLS-1$
		}
		return new Path(pathString);
	}

	/**
	 * Obtain and return the user name from the request headers.
	 */
	private static String getUserName(HttpServletRequest req) {
		return req.getRemoteUser();
	}

}
