package org.eclipse.orion.internal.server.hosting;

import java.io.IOException;
import java.net.InetAddress;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;

/**
 * If a request is for a resource on a running hosted site (based on Host header), this filter 
 * forwards the request to the HostedSiteServlet to be handled.
 */
public class HostedSiteRequestFilter implements Filter {

	private static final String HOSTED_SITE_ALIAS = "/hosted"; //$NON-NLS-1$

	private ISiteHostingService siteHostingService;
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		this.siteHostingService = HostingActivator.getDefault().getHostingService();
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
		if (siteHostingService != null) {
			HttpServletRequest httpReq = (HttpServletRequest) req;
			String host = getHostWithoutPort(httpReq);
			if (host != null) {
				String requestUri = httpReq.getRequestURI();
				if (siteHostingService.isHosted(host) && !requestUri.startsWith(HOSTED_SITE_ALIAS)) {
					// Forward to /hosted/<host>
					RequestDispatcher rd = httpReq.getRequestDispatcher(HOSTED_SITE_ALIAS + "/" + host + requestUri); //$NON-NLS-1$
					rd.forward(req, resp);
					return;
				}
			}
		}
		chain.doFilter(req, resp);
	}

	private static String getHostWithoutPort(HttpServletRequest req) {
		String host = req.getHeader("Host"); //$NON-NLS-1$
		if (host != null) {
			int i = host.lastIndexOf(":"); //$NON-NLS-1$
			if (i != -1) {
				return host.substring(0, i);
			}
			return host;
		}
		return null;
	}

	@Override
	public void destroy() {
	}

}
