package org.eclipse.orion.internal.server.servlets.docker;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;

public class DockerServlet extends OrionServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setStatus(HttpServletResponse.SC_OK);
		JSONObject jsonResult = new JSONObject();
		try {
			jsonResult.put("containerId", "4a88a5dd2f48");
			jsonResult.put("containerAddress", "127.0.0.1");
			OrionServlet.writeJSONResponse(req, resp, jsonResult);
		} catch (JSONException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
}
