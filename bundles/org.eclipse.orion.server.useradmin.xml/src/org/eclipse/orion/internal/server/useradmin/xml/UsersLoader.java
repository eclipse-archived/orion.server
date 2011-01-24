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
package org.eclipse.orion.internal.server.useradmin.xml;

import org.eclipse.orion.server.useradmin.Role;
import org.eclipse.orion.server.useradmin.User;

import org.eclipse.orion.server.core.LogHelper;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class UsersLoader {

	static final String MAIN_NODE = "WebIdeUsers";
	static final String ROLES = "roles";
	static final String ROLE = "role";
	static final String ROLE_NAME = "name";
	static final String USERS = "users";
	static final String USER = "user";
	static final String USER_LOGIN = "login";
	static final String USER_NAME = "name";
	static final String USER_PASSWORD = "password";
	static final String USER_ROLES = "roles";
	static final String USER_ROLE = "role";
	static final String USER_ROLE_NAME = "name";

	private final URL entry;
	private Document doc;

	private Map<String, User> usersMap;
	private Map<String, Role> rolesMap;

	public UsersLoader(final URL entry) {
		super();
		this.entry = entry;
	}

	public void buildMaps() throws SAXException, IOException, ParserConfigurationException {
		if (doc == null) {
			parseDocument();
		}
		if (!doc.getDocumentElement().getNodeName().equals(MAIN_NODE)) {
			throw new SAXParseException("Invalid file format, does not begin with " + MAIN_NODE, null);
		}

		rolesMap = new HashMap<String, Role>();
		usersMap = new HashMap<String, User>();

		NodeList roleNodes = ((Element) doc.getElementsByTagName(ROLES).item(0)).getElementsByTagName(ROLE);
		for (int i = 0; i < roleNodes.getLength(); i++) {
			Element roleElement = (Element) roleNodes.item(i);
			Role role = new Role(roleElement.getAttribute(ROLE_NAME), Role.ROLE);
			rolesMap.put(role.getName(), role);
		}

		NodeList userNodes = ((Element) doc.getElementsByTagName(USERS).item(0)).getElementsByTagName(USER);

		for (int i = 0; i < userNodes.getLength(); i++) {
			Element userElement = (Element) userNodes.item(i);
			User user = new User(userElement.getAttribute(USER_LOGIN), userElement.getAttribute(USER_NAME), userElement.getAttribute(USER_PASSWORD));

			NodeList userRoles = userElement.getElementsByTagName(USER_ROLES);
			if (userRoles.getLength() > 0) {
				NodeList userRole = ((Element) userRoles.item(0)).getElementsByTagName(USER_ROLE);
				for (int role_id = 0; role_id < userRole.getLength(); role_id++) {
					Element roleElem = (Element) userRole.item(role_id);
					user.addRole(rolesMap.get(roleElem.getAttribute(USER_ROLE_NAME)));
				}
			}
			usersMap.put(user.getLogin(), user);
		}
	}

	public Map<String, User> getUsersMap() {
		return usersMap;
	}

	public Map<String, Role> getRolesMap() {
		return rolesMap;
	}

	private void parseDocument() throws SAXException, IOException, ParserConfigurationException {
		doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(entry.openStream());
		doc.getDocumentElement().normalize();
	}

	public void flush() {
		try {
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer transformer = tFactory.newTransformer();

			URL fileURL = FileLocator.toFileURL(entry);
			File file = new File(fileURL.toURI());
			StreamResult result = new StreamResult(file);

			DOMSource source = new DOMSource(doc);
			transformer.transform(source, result);
		} catch (TransformerException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USERADMIN_XML, 1, "A transformation error occured during flush", e));
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USERADMIN_XML, 1, "Couldn't convert bundle entry to file", e));
		} catch (URISyntaxException e) {
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USERADMIN_XML, 1, "File URL cannot be parsed as a URI reference", e));
		}
	}

	public void createRole(String name) {
		Element newRoleElement = doc.createElement(ROLE);
		newRoleElement.setAttribute(ROLE_NAME, name);

		NodeList roleNodes = doc.getElementsByTagName(ROLES);
		roleNodes.item(0).appendChild(newRoleElement);
		flush();
	}

	public boolean removeRole(String name) {
		NodeList roleNodes = ((Element) doc.getElementsByTagName(ROLES).item(0)).getElementsByTagName(ROLE);
		for (int i = 0; i < roleNodes.getLength(); i++) {
			Element roleElement = (Element) roleNodes.item(i);
			if (roleElement.getAttribute(ROLE_NAME).equals(name)) {
				roleElement.getParentNode().removeChild(roleElement);
				flush();
				return true;
			}
		}
		return false;
	}

	public void createUser(org.eclipse.orion.server.useradmin.User user) {
		Element newUserElement = doc.createElement(USER);
		newUserElement.setAttribute(USER_LOGIN, user.getLogin());
		newUserElement.setAttribute(USER_NAME, user.getName());
		newUserElement.setAttribute(USER_PASSWORD, user.getPassword());
		if (user.getRoles().size() > 0) {
			Element rolesElement = doc.createElement(ROLES);
			newUserElement.appendChild(rolesElement);
			for (Iterator i = user.getRoles().iterator(); i.hasNext();) {
				Role role = (Role) i.next();
				Element newRoleElement = doc.createElement(ROLE);
				newRoleElement.setAttribute(ROLE_NAME, role.getName());
				rolesElement.appendChild(newRoleElement);
			}
		}

		NodeList userNodes = doc.getElementsByTagName(USERS);
		userNodes.item(0).appendChild(newUserElement);
		flush();
	}

	public boolean deleteUser(org.osgi.service.useradmin.User user) {
		NodeList userNodes = ((Element) doc.getElementsByTagName(USERS).item(0)).getElementsByTagName(USER);
		for (int i = 0; i < userNodes.getLength(); i++) {
			Element userElement = (Element) userNodes.item(i);
			if (userElement.getAttribute(USER_LOGIN).equals(user.getCredentials().get(USER_LOGIN))) {
				userElement.getParentNode().removeChild(userElement);
				flush();
				return true;
			}
		}
		return false;
	}

	public boolean updateUser(String oldLogin, org.eclipse.orion.server.useradmin.User user) {
		NodeList userNodes = ((Element) doc.getElementsByTagName(USERS).item(0)).getElementsByTagName(USER);
		for (int i = 0; i < userNodes.getLength(); i++) {
			Element userElement = (Element) userNodes.item(i);
			if (userElement.getAttribute(USER_LOGIN).equals(oldLogin)) {
				userElement.getParentNode().removeChild(userElement);
				createUser(user);
				flush();
				return true;
			}
		}
		return false;
	}
}
