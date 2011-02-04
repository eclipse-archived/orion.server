package org.eclipse.orion.internal.server.sitebuild;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

/**
 * Forwards requests for host 127.0.0.x (where x > 1) to the Proxy Servlet
 */
public class SiteBuildProxyFilter implements Filter {

	@Override
	public void init(FilterConfig arg0) throws ServletException {
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpReq = (HttpServletRequest) req;
		String host = httpReq.getHeader("Host");
		String requestURL = httpReq.getRequestURI();
		if (host.startsWith("127.0.0.") && !host.endsWith(".1") && !requestURL.startsWith("/sitebuild")) {
			// Forward to proxy servlet
			RequestDispatcher rd = httpReq.getRequestDispatcher("/sitebuild" + requestURL);
			rd.forward(req, resp);
			return;
		}
		chain.doFilter(req, resp);
	}

	@Override
	public void destroy() {
	}

}
