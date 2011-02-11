package org.eclipse.orion.internal.server.servlets.hosting;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

/**
 * If a request is for a file on a running hosted site, this filter forwards the request to 
 * the HostedSiteServlet to be handled.
 */
public class HostedSiteRequestFilter implements Filter {

	private static final String HOSTED_SITE_ALIAS = "/hosted"; //$NON-NLS-1$

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
		// TODO consult HostedSitesTable
		String host = req.getHeader("Host"); //$NON-NLS-1$
		return host.startsWith("127.0.0.") && !host.endsWith(".1"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public void destroy() {
	}

}
