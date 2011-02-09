package org.eclipse.orion.internal.server.servlets.build;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

/**
 * If a request is for a file on a running hosted site, this filter forwards the request to 
 * the HostedSiteServlet to be handled.
 */
public class HostedSiteFilter implements Filter {

	private static final String HOSTED_SITE_ALIAS = "/hosted";

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpReq = (HttpServletRequest) req;
		String requestUri = httpReq.getRequestURI();
		if (isForHostedSite(httpReq) && !requestUri.startsWith(HOSTED_SITE_ALIAS)) {
			RequestDispatcher rd = httpReq.getRequestDispatcher(HOSTED_SITE_ALIAS + requestUri);
			rd.forward(req, resp);
			return;
		}
		chain.doFilter(req, resp);
	}

	private boolean isForHostedSite(HttpServletRequest req) {
		String host = req.getHeader("Host");
		// TODO consult HostedSiteTable
		return host.startsWith("127.0.0.") && !host.endsWith(".1");
	}

	@Override
	public void destroy() {
	}

}
