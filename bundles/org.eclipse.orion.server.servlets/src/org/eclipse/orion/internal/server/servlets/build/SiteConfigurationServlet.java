package org.eclipse.orion.internal.server.servlets.build;


import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;

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
			// Get all site configurations
			doGetSiteConfigurations(req, resp);
			return;
		} else if (pathInfo.segmentCount() == 1) {
			// Get a site configuration
			SiteConfiguration siteConfig = getExistingSiteConfig(req, resp);
			if (siteConfigurationResourceHandler.handleRequest(req, resp, siteConfig)) {
				return;
			}
		}
		super.doGet(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 0) {
			// Create a new site configuration, and possibly start it
			doCreateSiteConfiguration(req, resp);
		} else if (pathInfo.segmentCount() == 1) {
			// Start/stop site configuration
			SiteConfiguration siteConfig = getExistingSiteConfig(req, resp);
			siteConfigurationResourceHandler.handleRequest(req, resp, siteConfig);
		}
		super.doPost(req, resp);
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 1) {
			// Update site configuration
			SiteConfiguration siteConfig = getExistingSiteConfig(req, resp);
			siteConfigurationResourceHandler.handleRequest(req, resp, siteConfig);
		}
		super.doPut(req, resp);
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		if (getPathInfo(req).segmentCount() == 1) {
			// Delete site configuration
			SiteConfiguration siteConfig = getExistingSiteConfig(req, resp);
			siteConfigurationResourceHandler.handleRequest(req, resp, siteConfig);
			return;
		}
		super.doDelete(req, resp);
	}

	/**
	 * @return The SiteConfiguration whose id matches the 0th segment of the Request pathInfo, or null if
	 * no SiteConfiguration exists with that id.
	 */
	private SiteConfiguration getExistingSiteConfig(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String userName = getUserName(req);
		WebUser user = WebUser.fromUserName(userName);
		IPath pathInfo = getPathInfo(req);
		if (pathInfo.segmentCount() == 1) {
			return SiteConfiguration.fromId(user, pathInfo.segment(0));
		}
		return null;
	}

	private boolean doGetSiteConfigurations(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String userName = getUserName(req);
		try {
			WebUser user = WebUser.fromUserName(userName);
			writeJSONResponse(req, resp, user.getSiteConfigurationsJSON(ServletResourceHandler.getURI(req)));
		} catch (Exception e) {
			handleException(resp, "An error occurred while obtaining site configurations", e); //$NON-NLS-1$
		}
		return true;
	}

	/**
	 * Create, and possibly start, a site configuration
	 */
	private void doCreateSiteConfiguration(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		try {
			WebUser user = WebUser.fromUserName(getUserName(req));
			JSONObject requestJson = readJSONRequest(req);
			String name = computeName(req, requestJson);
			if (name == null || name.equals("")) { //$NON-NLS-1$
				getStatusHandler().handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Site configuration name not specified", null)); //$NON-NLS-1$
			} else {
				SiteConfiguration siteConfig = SiteConfigurationResourceHandler.createFromJSON(user, name, requestJson);
				siteConfigurationResourceHandler.handleRequest(req, resp, siteConfig);
			}
		} catch (CoreException e) {
			handleException(resp, e.getStatus());
		} catch (IOException e) {
			handleException(resp, "An error occurred while reading the request", e); //$NON-NLS-1$
		} catch (JSONException e) {
			getStatusHandler().handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request body", e)); //$NON-NLS-1$
		}
	}

	private static String computeName(HttpServletRequest req, JSONObject requestBody) {
		// Try Slug first
		String name = req.getHeader(ProtocolConstants.HEADER_SLUG);
		if (name == null || name.equals("")) { //$NON-NLS-1$
			// Then try request body
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

	/**
	 * Verify that the user name is valid. Returns <code>true</code> if the
	 * name is valid and false otherwise. If invalid, this method will handle
	 * filling in the servlet response.
	 */
	//	private boolean checkUser(HttpServletRequest request, HttpServletResponse response) throws ServletException {
	//		if (getUserName(request) == null) {
	//			handleException(response, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "User name not specified"), HttpServletResponse.SC_FORBIDDEN); //$NON-NLS-1$
	//			return false;
	//		}
	//		return true;
	//	}
}
