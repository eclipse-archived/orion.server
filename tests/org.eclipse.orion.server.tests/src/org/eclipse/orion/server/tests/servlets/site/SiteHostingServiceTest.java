package org.eclipse.orion.server.tests.servlets.site;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.List;

import org.eclipse.orion.internal.server.hosting.IHostedSite;
import org.eclipse.orion.internal.server.hosting.SiteHostingConfig;
import org.eclipse.orion.internal.server.hosting.SiteHostingService;
import org.eclipse.orion.internal.server.hosting.SiteInfo;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.junit.Test;

/**
 * Unit tests dealing with Site Hosting internals
 */
public class SiteHostingServiceTest {

	protected IMetaStore getMetaStore() {
		return OrionConfiguration.getMetaStore();
	}

	@Test
	public void testSiteHostingConfigParsing() throws Exception {
		SiteHostingConfig config = SiteHostingConfig.getSiteHostingConfig("*.example.org,  127.0.0.3");
		List<String> hosts = config.getHosts();
		assertEquals(hosts.size(), 2);
		assertEquals(hosts.get(0), "*.example.org"); //$NON-NLS-1$
		assertEquals(hosts.get(1), "127.0.0.3"); //$NON-NLS-1$
	}

	@Test
	public void testSiteHostingServiceStart() throws Exception {
		IMetaStore metaStore = getMetaStore();

		SiteHostingService hostingService = new SiteHostingService(SiteHostingConfig.getSiteHostingConfig("https://*.sites.example.org:1234"));

		UserInfo user = new UserInfo();
		user.setUniqueId("A");
		user.setUserName("carlos");
		metaStore.createUser(user);

		SiteInfo site = SiteInfo.newSiteConfiguration(user, "mysite", "myworkspace");
		site.setId("s1");
		site.setName("Some site");
		site.setHostHint("foo");
		site.save(user);

		hostingService.start(site, user, "http://whatever/dontcare", new URI("http", null, "test", 80, null, null, null));

		IHostedSite vhost = hostingService.get(site, user);
		assertEquals("https://foo.sites.example.org:1234", vhost.getUrl());
	}

	@Test
	public void testSiteHostingServiceStartHostnameTaken() throws Exception {
		IMetaStore metaStore = getMetaStore();

		SiteHostingService hostingService = new SiteHostingService(SiteHostingConfig.getSiteHostingConfig("https://*.sites.example.org"));

		UserInfo user = new UserInfo();
		user.setUniqueId("A");
		user.setUserName("carlos");
		metaStore.createUser(user);

		SiteInfo site1 = SiteInfo.newSiteConfiguration(user, "site1", "myworkspace");
		site1.setId("s1");
		site1.setName("Site 1");
		site1.setHostHint("foo");
		site1.save(user);

		SiteInfo site2 = SiteInfo.newSiteConfiguration(user, "site2", "myworkspace");
		site1.setId("s2");
		site2.setName("Site 2");
		site2.setHostHint("foo");
		site2.save(user);

		hostingService.start(site1, user, "http://whatever/dontcare", new URI("http", null, "test", 80, null, null, null));
		hostingService.start(site2, user, "http://whatever/dontcare", new URI("http", null, "test", 80, null, null, null));

		// Expect: site1 acquires the "foo" URL, and site2 has to settle for foo + {suffix "0"}
		assertEquals("https://foo.sites.example.org", hostingService.get(site1, user).getUrl());
		assertEquals("https://foo0.sites.example.org", hostingService.get(site2, user).getUrl());
	}

	@Test
	public void testSiteHostingServiceMatchesVirtualHost() throws Exception {
		IMetaStore metaStore = getMetaStore();

		SiteHostingService hostingService = new SiteHostingService(SiteHostingConfig.getSiteHostingConfig("https://*.sites.example.org:1234"));

		UserInfo user = new UserInfo();
		user.setUniqueId("A");
		user.setUserName("carlos");
		metaStore.createUser(user);

		SiteInfo site = SiteInfo.newSiteConfiguration(user, "mysite", "myworkspace");
		site.setId("s1");
		site.setName("Some site");
		site.setHostHint("foo");
		site.save(user);

		// Should be recognized as matching a virtual host 
		assertEquals(true, hostingService.matchesVirtualHost("fizzbuzz.sites.example.org"));
	}

}
