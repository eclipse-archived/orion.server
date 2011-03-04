package org.eclipse.orion.internal.server.hosting;

import java.net.*;
import java.util.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.LogHelper;

/**
 * Stores the IP addresses and domains that may be allocated to hosted sites, and the port number
 * that sites will be accessed on. The server administrator can control the IP/domain configuration
 * by passing a parameter to the Orion server at launch.<p>
 * 
 * The parameter is a string with the form <code>[domains=domainList][,][IPs=ipList]</code>,
 * where <code>domainList</code> is a comma-separated list of domains (which may include wildcards), and
 * <code>ipList</code> is a comma-separated list of IP addresses.<p>
 * 
 * Examples:
 * <dl>
 * <dt><code>IPs=127.0.0.2,127.0.0.3</code></dt>
 * <dd>Makes 2 IPs available for allocation.</dd>
 * 
 * <dt><code>domains=site.myorion.net</code></dt>
 * <dd>Makes 1 domain available for allocation.</dd>
 * 
 * <dt><code>domains=*.myorion.net</code></dt>
 * <dd>Makes all of <code>*.myorion.net</code> available for allocation. Sites will be given subdomains,
 * for example <code>site1.myorion.net</code>, <code>site2.myorion.net</code>, etc.</dd>
 * 
 * <dt><code>IPs=127.0.0.2,domains=*.myorion.net</code></dt>
 * <dd>Equivalent to the previous example; when both domains and IPs are provided, domains are given priority.</dd>
 * </dl>
 */
public class SiteHostingConfig {

	private int hostingPort;
	private Set<String> domains;
	private Set<InetAddress> ipAddresses;

	private SiteHostingConfig(int port, Set<String> domains, Set<InetAddress> ips) {
		this.hostingPort = port;
		this.domains = domains;
		this.ipAddresses = ips;
	}

	public int getHostingPort() {
		return this.hostingPort;
	}

	public Set<String> getDomains() {
		return Collections.unmodifiableSet(domains);
	}

	public Set<InetAddress> getIpAddresses() {
		return Collections.unmodifiableSet(ipAddresses);
	}

	/**
	 * @param hostInfo String containing the user-supplied site configuration info.
	 * @return A hosting configuration parsed from <code>s</code>. If <code>s</code> is <code>null</code>,
	 * returns a default configuration.
	 */
	static SiteHostingConfig getSiteHostingConfig(int port, String hostInfo) {
		return hostInfo == null ? getDefault(port) : fromString(port, hostInfo);
	}

	/**
	 * Parses a SiteHostingConfig from the <code>hostInfo</code> string.
	 */
	private static SiteHostingConfig fromString(int port, String hostInfo) {
		Set<String> domains = new HashSet<String>();
		Set<InetAddress> ips = new HashSet<InetAddress>();
		StringTokenizer st = new StringTokenizer(hostInfo, "=,", true); //$NON-NLS-1$
		try {
			String token = st.nextToken();
			if (token.equalsIgnoreCase("ips")) { //$NON-NLS-1$
				parseIps(st, domains, ips, false);
			} else if (token.equalsIgnoreCase("domains")) { //$NON-NLS-1$
				parseDomains(st, domains, ips, false);
			}
		} catch (NoSuchElementException e) {
			LogHelper.log(new Status(IStatus.ERROR, HostingActivator.PI_SERVER_HOSTING, "Error parsing hosting configuration", e));
		} catch (UnknownHostException e) {
			LogHelper.log(new Status(IStatus.ERROR, HostingActivator.PI_SERVER_HOSTING, "Error parsing hosting configuration", e));
		}
		return new SiteHostingConfig(port, domains, ips);
	}

	/**
	 * @param st Tokenizer whose first token (if any) is the = after "IPs"
	 * @param domains
	 * @param ips
	 * @param domainsDone True if the domains= section was already parsed.
	 * @throws UnknownHostException
	 */
	private static void parseIps(StringTokenizer st, Set<String> domains, Set<InetAddress> ips, boolean domainsDone) throws UnknownHostException {
		if (!st.nextToken().equals("=")) //$NON-NLS-1$
			throw new NoSuchElementException("Expected =");

		for (; st.hasMoreTokens();) {
			String value = st.nextToken();
			if (value.equals(",")) //$NON-NLS-1$
				continue;
			else if (!domainsDone && value.equalsIgnoreCase("domains")) //$NON-NLS-1$
				parseDomains(st, domains, ips, true);
			else
				ips.add(InetAddress.getByName(value));
		}
	}

	/**
	 * @param st Tokenizer whose first token (if any) is the = after "domains"
	 * @param domains
	 * @param ips
	 * @param ipsDone True if the IPs= section was already parsed.
	 * @throws UnknownHostException
	 */
	private static void parseDomains(StringTokenizer st, Set<String> domains, Set<InetAddress> ips, boolean ipsDone) throws UnknownHostException {
		if (!st.nextToken().equals("=")) //$NON-NLS-1$
			throw new NoSuchElementException("Expected =");

		for (; st.hasMoreTokens();) {
			String tok = st.nextToken();
			if (tok.equals(",")) //$NON-NLS-1$
				continue;
			else if (!ipsDone && tok.equalsIgnoreCase("ips")) //$NON-NLS-1$
				parseIps(st, domains, ips, true);
			else
				domains.add(tok);
		}
	}

	/**
	 * @param port
	 * @return A default SiteHostingConfig based on the platform and network setup. If possible,
	 * the config will include some IP addresses that point to the loopback device.
	 */
	private static SiteHostingConfig getDefault(int port) {
		Set<InetAddress> aliases = new HashSet<InetAddress>();

		// TODO: this guessing is fragile and only useful for self-hosting. Remove it
		if (System.getProperty("os.name").startsWith("Mac OS X")) { //$NON-NLS-1$ //$NON-NLS-2$
			// In OS X, although all 127.x.x.x addresses report isLoopback() == true, only 127.0.0.1
			// actually routes to localhost. So we can't do much here.
		} else {
			InetAddress loopbackIp = getLoopbackAddress();
			try {
				// Try to allocate IPs from the last byte of the loopback address.
				// TODO: It'd be more correct to use range 127.0.0.0 - 127.255.255.255
				byte[] address = loopbackIp.getAddress();
				byte lastByte = address[address.length - 1];
				for (int i = lastByte + 1; i <= 0xff; i++) {
					byte[] derivedAddress = address.clone();
					derivedAddress[address.length - 1] = (byte) i;
					aliases.add(InetAddress.getByAddress(derivedAddress));
				}
			} catch (UnknownHostException e) {
				// Should not happen
				LogHelper.log(new Status(IStatus.ERROR, HostingActivator.PI_SERVER_HOSTING, e.getMessage(), e));
			}
		}
		return new SiteHostingConfig(port, Collections.<String> emptySet(), aliases);
	}

	/**
	 * @return The IPv4 loopback device, or null if it couldn't be determined.
	 */
	private static InetAddress getLoopbackAddress() {
		try {
			for (NetworkInterface interfaze : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (interfaze.isLoopback()) {
					for (InetAddress address : Collections.list(interfaze.getInetAddresses())) {
						if (address instanceof Inet4Address) {
							return address;
						}
					}
				}
			}
		} catch (SocketException e) {
			LogHelper.log(new Status(IStatus.ERROR, HostingActivator.PI_SERVER_HOSTING, e.getMessage(), e));
		}
		return null;
	}
}
