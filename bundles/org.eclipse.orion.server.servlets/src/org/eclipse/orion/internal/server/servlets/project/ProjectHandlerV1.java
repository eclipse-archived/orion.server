package org.eclipse.orion.internal.server.servlets.project;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.project.Project;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;

public class ProjectHandlerV1 extends ServletResourceHandler<Project> {

	final ServletResourceHandler<IStatus> statusHandler;

	/**
	 * @param statusHandler
	 */
	public ProjectHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super();
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, Project project) throws ServletException {

		switch (getMethod(request)) {
			case GET :
				handleGet(request, response, project);
				return true;
			case POST :
				handlePost(request, response, project);
				return true;
			case PUT :
				handlePut(request, response, project);
				return true;

		}
		return false;
	}

	private void handlePut(HttpServletRequest request, HttpServletResponse response, Project project) throws ServletException {
		if (!project.exists()) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Project " + project.getDirectory().getFullName() + " does not exist.", null));
		}
		try {
			JSONObject requestJson = OrionServlet.readJSONRequest(request);
			if (requestJson.has("Depenency")) {
				JSONObject depenency = requestJson.getJSONObject("Depenency");
				project.addDependency(depenency.optString("Name"), depenency.optString("Type"), depenency.optString("Location"));
			}
		} catch (IOException e) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not read request.", e));
		} catch (JSONException e) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not read request.", e));
		} catch (CoreException e) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not add depenency.", e));
		}
	}

	private void createProject(HttpServletRequest request, HttpServletResponse response, Project project, Path projectLocationPath) throws ServletException {
		try {
			if (project.exists()) {
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Project " + project.getDirectory().getFullName() + " already exists.", null));
				return;
			}
			project.initialize();
			JSONObject projectJson = project.toJson();
			projectJson.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(URIUtil.append(getURI(request).resolve("/project"), projectLocationPath.segment(projectLocationPath.segmentCount() - 2)), projectLocationPath.lastSegment()).toString());
			projectJson.put(ProtocolConstants.KEY_CONTENT_LOCATION, URIUtil.append(URIUtil.append(getURI(request).resolve("/file"), projectLocationPath.segment(projectLocationPath.segmentCount() - 2)), projectLocationPath.lastSegment()).toString() + "/?depth=1");
			OrionServlet.writeJSONResponse(request, response, projectJson);
		} catch (JSONException e) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not build project response.", e));
		} catch (CoreException e) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not initialize project.", e));
		} catch (IOException e) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not read request.", e));
		}
	}

	private void handlePost(HttpServletRequest request, HttpServletResponse response, Project project) throws ServletException {
		if (project == null) {
			try {
				JSONObject requestJson = OrionServlet.readJSONRequest(request);
				String contentLocation = requestJson.optString("ContentLocation");
				Path projectLocationPath = new Path(new URI(contentLocation).getPath());
				if (projectLocationPath.segmentCount() < 2) {
					statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid project location.", null));
					return;
				}
				ProjectInfo projectInfo = OrionConfiguration.getMetaStore().readProject(projectLocationPath.segment(projectLocationPath.segmentCount() - 2), projectLocationPath.lastSegment());
				project = Project.fromProjectInfo(projectInfo);
				createProject(request, response, project, projectLocationPath);
			} catch (URISyntaxException e) {
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid project location.", e));
			} catch (CoreException e) {
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not initialize project.", e));
			} catch (JSONException e) {
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not build project response.", e));
			} catch (IOException e) {
				statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not read request.", e));
			}
		} else {
			createProject(request, response, project, new Path(request.getPathInfo()));
		}

	}

	private void handleGet(HttpServletRequest request, HttpServletResponse response, Project project) throws ServletException {
		if (project == null || !project.exists()) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Could not find project", null));
			return;
		}
		try {
			JSONObject projectJson = project.toJson();
			URI uri = getURI(request);
			String pathInfo = request.getPathInfo();
			Path path = new Path(pathInfo);
			projectJson.put(ProtocolConstants.KEY_LOCATION, uri);
			projectJson.put(ProtocolConstants.KEY_CONTENT_LOCATION, URIUtil.append(URIUtil.append(uri.resolve("/file"), path.segment(0)), path.segment(1)).toString() + "/?depth=1");
			OrionServlet.writeJSONResponse(request, response, projectJson);
		} catch (JSONException e) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal error while creating response", e));
		} catch (IOException e) {
			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal error while creating response", e));
		}
	}
}
