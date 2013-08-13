package org.eclipse.orion.internal.server.servlets.project;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.project.Project;
import org.eclipse.orion.server.servlets.OrionServlet;

public class ProjectServlet extends OrionServlet {

	ProjectHandlerV1 projectHandlerV1 = new ProjectHandlerV1(getStatusHandler());

	/**
	 * 
	 */
	private static final long serialVersionUID = -1615454357717722084L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfo = req.getPathInfo();
		if (pathInfo == null) {
			projectHandlerV1.handleRequest(req, resp, null);
			return;
		}
		Path path = new Path(pathInfo);
		if (path == null || path.segmentCount() != 2) {
			handleException(resp, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, "Invalid project request"), HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		try {
			ProjectInfo project = OrionConfiguration.getMetaStore().readProject(path.segment(0), path.segment(1));
			projectHandlerV1.handleRequest(req, resp, Project.fromProjectInfo(project));
		} catch (CoreException e) {
			handleException(resp, "Could not find project", e);
		}
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}

}
