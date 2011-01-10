/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.webide.server.configurator.configuration;

import static org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat.AUTH_NAME;
import static org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat.AUTH_PROPERTIES;
import static org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat.AUTH_PROPERTY;
import static org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat.AUTH_PROPERTY_KEY;
import static org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat.CONF_ROOT;
import static org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat.HTTPS_ENABLED;
import static org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat.HTTPS_PORT;
import static org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat.SSL_KEYPASSWORD;
import static org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat.SSL_KEYSTORE;
import static org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat.SSL_PASSWORD;
import static org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat.SSL_PROTOCOL;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class PropertyReader {

	private Properties properties = new Properties();

	private String authName = "";

	private Boolean httpsEnabled = Boolean.FALSE;

	private Integer httpsPort;

	private String sslKeystore;

	private String sslPassword;

	private String sslKeyPassword;

	private String sslProtocol;

	public PropertyReader(InputStream configuration) throws SAXException,
			IOException, ParserConfigurationException {
		Document doc = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder().parse(configuration);
		doc.getDocumentElement().normalize();
		if (!doc.getDocumentElement().getNodeName().equals(CONF_ROOT)) {
			throw new SAXParseException("Configuration file should begin with "
					+ CONF_ROOT, null);
		}

		authName = getNodeValue(AUTH_NAME, doc);
		httpsEnabled = Boolean.valueOf(getNodeValue(HTTPS_ENABLED, doc));
		httpsPort = Integer.valueOf(getNodeValue(HTTPS_PORT, doc));
		sslKeystore = getNodeValue(SSL_KEYSTORE, doc);
		sslPassword = getNodeValue(SSL_PASSWORD, doc);
		sslKeyPassword = getNodeValue(SSL_KEYPASSWORD, doc);
		sslProtocol = getNodeValue(SSL_PROTOCOL, doc);

		NodeList propertiesNodes = doc.getElementsByTagName(AUTH_PROPERTIES);
		if (propertiesNodes.getLength() == 0) {
			return;
		}
		propertiesNodes = ((Element) propertiesNodes.item(0))
				.getElementsByTagName(AUTH_PROPERTY);
		for (int i = 0; i < propertiesNodes.getLength(); i++) {
			Element property = (Element) propertiesNodes.item(i);
			properties.put(property.getAttribute(AUTH_PROPERTY_KEY), property
					.getFirstChild().getNodeValue());
		}
	}

	public Properties getProperties() {
		return properties;
	}

	public String getAuthName() {
		return authName;
	}

	public Boolean getHttpsEnabled() {
		return httpsEnabled;
	}

	public Integer getHttpsPort() {
		return httpsPort;
	}

	public String getSslKeystore() {
		return sslKeystore;
	}

	public String getSslPassword() {
		return sslPassword;
	}

	public String getSslKeyPassword() {
		return sslKeyPassword;
	}

	public String getSslProtocol() {
		return sslProtocol;
	}

	private String getNodeValue(String tagName, Document doc) {
		Node node = doc.getElementsByTagName(tagName).item(0);
		String value = node.getFirstChild().getNodeValue();
		return value;
	}
}
