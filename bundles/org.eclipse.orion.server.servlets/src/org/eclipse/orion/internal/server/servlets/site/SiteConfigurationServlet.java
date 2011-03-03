package org.eclipse.orion.internal.server.servlets.site;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.hosting.*;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
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
				SiteConfiguration siteConfig = null;
				try {
					siteConfig = doCreateSiteConfiguration(req, resp);
					doAction(req, resp, siteConfig, false);
				} catch (CoreException e) {
					// If start/stop failed, try to clean up
					if (siteConfig != null) {
						// Remove site config from user's list
						WebUser user = WebUser.fromUserName(getUserName(req));
						user.removeSiteConfiguration(siteConfig);

						// Also delete the site configuration itself since it can never be used
						try {
							siteConfig.delete();
						} catch (CoreException deleteException) {
							// Ignore
						}
					}
					throw e;
				}
				if (siteConfigurationResourceHandler.handleRequest(req, resp, siteConfig)) {
					return;
				}
			} else if (pathInfo.segmentCount() == 1) {
				// Start/stop site configuration
				SiteConfiguration siteConfig = getExistingSiteConfig(req, resp);
				doAction(req, resp, siteConfig, true);
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
	 * Performs an action, given by the Action header, on a site configuration.
	 * @param siteConfig
	 * @param actionRequired <code>true</code> if a missing Action header should cause a failure response.
	 * @throws CoreException If a bogus action was found, or if <code>actionRequired == true</code> but no action
	 * was found, or if the hosting service threw an exception while performing the action.
	 */
	private void doAction(HttpServletRequest req, HttpServletResponse resp, SiteConfiguration siteConfig, boolean actionRequired) throws CoreException {
		if (siteConfig == null)
			return;
		WebUser user = WebUser.fromUserName(getUserName(req));
		String action = req.getHeader(SiteConfigurationConstants.HEADER_ACTION);
		try {
			if ("start".equalsIgnoreCase(action)) { //$NON-NLS-1$
				ISiteHostingService service = getHostingService();
				String editServer = "http://" + req.getHeader("Host"); //$NON-NLS-1$ //$NON-NLS-2$
				service.start(siteConfig, user, editServer);
			} else if ("stop".equalsIgnoreCase(action)) { //$NON-NLS-1$
				ISiteHostingService service = getHostingService();
				service.stop(siteConfig, user);
			} else if (action == null && actionRequired) {
				throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Action is missing", null));
			} else if (action == null && !actionRequired) {
				// No action, but we can ignore it
			} else {
				throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Action {0} is not understood", action), null));
			}
		} catch (WrongHostingStatusException e) {
			// Give a descriptive status code for this case
			throw new CoreException(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_CONFLICT, e.getMessage(), e));
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

	// TODO: allow filtering by hosting state via query parameter?
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
			String workspace = requestJson.optString(SiteConfigurationConstants.KEY_WORKSPACE, null);
			SiteConfiguration siteConfig = SiteConfigurationResourceHandler.createFromJSON(user, name, workspace, requestJson);
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
		if (name == null || name.length() == 0) {
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
