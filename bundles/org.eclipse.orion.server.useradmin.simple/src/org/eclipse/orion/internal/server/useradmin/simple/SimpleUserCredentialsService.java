/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.useradmin.simple;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.core.metastore.SimpleUserPasswordUtil;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.useradmin.EmptyAuthorization;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.eclipse.orion.server.useradmin.Role;
import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserConstants;
import org.eclipse.orion.server.useradmin.WebIdeAuthorization;
import org.eclipse.orion.server.useradmin.servlets.UserServlet;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.Authorization;

/**
 * Implementation of the Orion credentials service on top of the simple meta store.
 * The meta data for each user is stored in the user.json in the simple meta store.
 * 
 * @author Anthony Hunter
 */
public class SimpleUserCredentialsService implements IOrionCredentialsService {

	IOrionUserProfileNode root = null;

	public static final String USER_PROPERTIES = "profileProperties"; //$NON-NLS-1$

	static final String ADMIN_LOGIN_VALUE = "admin"; //$NON-NLS-1$
	static final String ADMIN_NAME_VALUE = "Administrative User"; //$NON-NLS-1$

	// Map of email addresses to userids for an email cache
	private Map<String, String> emailCache = new HashMap<String, String>();

	// Map of openids to userids for an openid cache
	private Map<String, String> openidCache = new HashMap<String, String>();

	public SimpleUserCredentialsService() {
		super();
		initStorage();
	}

	private void initStorage() {

		try {
			IFileStore fileStore = OrionConfiguration.getUserHome(null);
			File rootLocation = fileStore.toLocalFile(EFS.NONE, null);
			root = new SimpleUserProfileRoot(rootLocation);
		} catch (CoreException e) {
			LogHelper.log(e);
		}

		// initialize the admin account
		String adminDefaultPassword = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_ADMIN_DEFAULT_PASSWORD);
		User admin = getUser(UserConstants.KEY_LOGIN, ADMIN_LOGIN_VALUE);
		if (admin == null && adminDefaultPassword != null) {
			// initialize the admin account in the IMetaStore
			UserInfo userInfo = new UserInfo();
			userInfo.setUserName(ADMIN_LOGIN_VALUE);
			userInfo.setFullName(ADMIN_NAME_VALUE);
			try {
				OrionConfiguration.getMetaStore().createUser(userInfo);
			} catch (CoreException e) {
				LogHelper.log(e);
			}
			admin = createUser(new User(userInfo.getUniqueId(), ADMIN_LOGIN_VALUE, ADMIN_NAME_VALUE, adminDefaultPassword));
		}

		// initialize the email and openid cache
		Collection<User> users = getUsers();
		users.clear();

		if (admin == null) {
			return;
		}

		// TODO: see bug 335699, the user storage should not configure authorization rules
		// it should add Admin role, which will be used during authorization process
		try {
			AuthorizationService.addUserRight(admin.getUid(), UserServlet.USERS_URI);
			AuthorizationService.addUserRight(admin.getUid(), UserServlet.USERS_URI + "/*"); //$NON-NLS-1$
		} catch (CoreException e) {
			LogHelper.log(e);
		}
	}

	public boolean deleteUser(User user) {
		// remove the email from the email cache
		String oldEmail = user.getEmail();
		if (oldEmail != null && !oldEmail.equals("")) {
			emailCache.remove(oldEmail.toLowerCase());
		}

		// remove the openid from the openid cache
		Enumeration<?> keys = user.getProperties().keys();
		while (keys.hasMoreElements()) {
			String property = (String) keys.nextElement();
			String value = (String) user.getProperty(property);
			if (property.equals("openid") && !value.equals("")) {
				// update the openid cache
				openidCache.remove(value);
			}
		}

		// Since the user profile is stored with the user metadata, it will automatically be deleted when the user metadata is deleted.
		return true;
	}

	public IStatus updateUser(String uid, User user) {
		if (!user.getUid().equals(uid)) {
			throw new RuntimeException("SimpleUserCredentialsService.updateUser: cannot change the user id " + uid + " for " + user.getName());
		}
		IOrionUserProfileNode userProfileNode = root.getUserProfileNode(uid);
		if (userProfileNode == null) {
			return new ServerStatus(IStatus.ERROR, 404, "User not found: " + uid, null);
		}
		createOrUpdateUser(userProfileNode, user);
		return new Status(IStatus.OK, Activator.PI_USER_SIMPLE, "User updated " + user.getLogin());
	}

	public User createUser(User newUser) {
		try {
			IOrionUserProfileNode userProfileNode = root.getUserProfileNode(newUser.getUid());
			if (!newUser.getLogin().equals(userProfileNode.get(UserConstants.KEY_LOGIN, ""))) {
				throw new RuntimeException("SimpleUserCredentialsService.createUser: names do not match for " + newUser.getLogin());
			}
			return createOrUpdateUser(userProfileNode, newUser);
		} catch (CoreException e) {
			LogHelper.log(e);
		}
		return null;
	}

	private User createOrUpdateUser(IOrionUserProfileNode userProfileNode, User user) {
		try {
			if (user.getName() != null) {
				userProfileNode.put(UserConstants.KEY_NAME, user.getName(), false);
			}
			if (user.getPassword() != null) {
				userProfileNode.put(UserConstants.KEY_PASSWORD, user.getPassword(), true);
			}
			if (user.getBlocked()) {
				userProfileNode.put(UserConstants.KEY_BLOCKED, String.valueOf(user.getBlocked()), false);
			} else {
				userProfileNode.remove(UserConstants.KEY_BLOCKED);
			}
			if (user.getEmail() != null) {
				String oldEmail = userProfileNode.get(UserConstants.KEY_EMAIL, null);
				if (user.getEmail().length() > 0 && !user.getEmail().equals(oldEmail)) {
					user.setConfirmationId();
				}
				userProfileNode.put(UserConstants.KEY_EMAIL, user.getEmail(), false);
				if (!user.getEmail().equals(oldEmail)) {
					if (oldEmail != null && !oldEmail.equals("")) {
						// remove the old email from the email cache
						emailCache.remove(oldEmail.toLowerCase());
					}
					if (!user.getEmail().equals("")) {
						// update the email cache
						emailCache.put(user.getEmail().toLowerCase(), user.getLogin());
					}
				}
			}
			if (user.getConfirmationId() == null)
				userProfileNode.remove(UserConstants.KEY_EMAIL_CONFIRMATION);
			else
				userProfileNode.put(UserConstants.KEY_EMAIL_CONFIRMATION, user.getConfirmationId(), false);
			IOrionUserProfileNode profileProperties = userProfileNode.getUserProfileNode(USER_PROPERTIES);
			String oldOpenid = profileProperties.get("openid", null);
			String newOpenid = "";
			Enumeration<?> keys = user.getProperties().keys();
			while (keys.hasMoreElements()) {
				String property = (String) keys.nextElement();
				String value = (String) user.getProperty(property);
				profileProperties.put(property, value, false);
				if (property.equals("openid")) {
					newOpenid = value;
				}
			}
			if (!newOpenid.equals(oldOpenid)) {
				// update the openid cache
				if (oldOpenid != null && !oldOpenid.equals("")) {
					// remove the old openid from the openid cache
					openidCache.remove(oldOpenid);
				}
				if (!newOpenid.equals("")) {
					// update the openid cache
					openidCache.put(newOpenid, user.getLogin());
				}
			}
			if (user.getLogin() != null) {
				userProfileNode.put(UserConstants.KEY_LOGIN, user.getLogin(), false);
			}
			userProfileNode.flush();
			return formUser(userProfileNode);
		} catch (CoreException e) {
			LogHelper.log(e);
		}
		return null;
	}

	public String getStoreName() {
		return "Orion"; //$NON-NLS-1$
	}

	public boolean canCreateUsers() {
		return true;
	}

	public Collection<User> getUsers() {
		Collection<User> users = new ArrayList<User>();
		for (String childName : root.childrenNames()) {
			IOrionUserProfileNode userProfileNode = root.getUserProfileNode(childName);
			try {
				User user = new User(childName, userProfileNode.get(UserConstants.KEY_LOGIN, childName), userProfileNode.get(UserConstants.KEY_NAME, ""), userProfileNode.get(UserConstants.KEY_PASSWORD, null) == null ? null : "" /* don't expose the password */); //$NON-NLS-1$ //$NON-NLS-2$
				user.setEmail(userProfileNode.get(UserConstants.KEY_EMAIL, "")); //$NON-NLS-1$
				if (!user.getEmail().equals("")) {
					// update the email cache
					emailCache.put(user.getEmail(), childName);
				}
				String blocked = userProfileNode.get(UserConstants.KEY_BLOCKED, "false");
				if (blocked.equals("true")) {
					user.setBlocked(true);
				}
				if (userProfileNode.get(UserConstants.KEY_EMAIL_CONFIRMATION, null) != null)
					user.setConfirmationId(userProfileNode.get(UserConstants.KEY_EMAIL_CONFIRMATION, null));
				String[] keys = userProfileNode.getUserProfileNode(USER_PROPERTIES).keys();
				if (keys.length > 0) {
					for (int i = 0; i < keys.length; i++) {
						String key = keys[i];
						String value = userProfileNode.getUserProfileNode(USER_PROPERTIES).get(key, "");
						user.addProperty(key, value);
						if (key.equals("openid")) {
							// update the openid cache
							openidCache.put(value, childName);
						}
					}
				}
				users.add(user);
			} catch (CoreException e) {
				LogHelper.log(e);
			}
		}
		Collections.sort((ArrayList<User>) users, new UserComparator());
		return users;
	}

	public class UserComparator implements Comparator<User> {
		public int compare(User user1, User user2) {
			return user1.getLogin().toLowerCase().compareTo(user2.getLogin().toLowerCase());
		}
	}

	public Role createRole(String name, int type) {
		throw new UnsupportedOperationException("Roles are not supported by SimpleUserCredentialsService.");
	}

	public IStatus removeRole(String name) {
		throw new UnsupportedOperationException("Roles are not supported by SimpleUserCredentialsService.");
	}

	public Role getRole(String name) {
		throw new UnsupportedOperationException("Roles are not supported by SimpleUserCredentialsService.");
	}

	public Role[] getRoles(String filter) throws InvalidSyntaxException {
		throw new UnsupportedOperationException("Roles are not supported by SimpleUserCredentialsService.");
	}

	public User getUser(String key, String value) {
		if (key.equals(UserConstants.KEY_LOGIN) || key.equals(UserConstants.KEY_UID)) {
			if (root.userProfileNodeExists(value)) {
				IOrionUserProfileNode userProfileNode = root.getUserProfileNode(value);
				return formUser(userProfileNode);
			}
		} else if (key.equals(UserConstants.KEY_EMAIL)) {
			// Use the email cache to lookup the user
			String email = value.toLowerCase();
			if (emailCache.containsKey(email)) {
				String uid = emailCache.get(email);
				IOrionUserProfileNode userProfileNode = root.getUserProfileNode(uid);
				return formUser(userProfileNode);
			}
		}
		return null;
	}

	private User formUser(IOrionUserProfileNode userProfileNode) {
		try {
			if (userProfileNode == null) {
				return null;
			}
			String encryptedPassword = userProfileNode.get(UserConstants.KEY_PASSWORD, "");
			String password = SimpleUserPasswordUtil.decryptPassword(encryptedPassword);
			String uid = userProfileNode.get(UserConstants.KEY_UID, "");
			String login = userProfileNode.get(UserConstants.KEY_LOGIN, "");
			String fullName = userProfileNode.get(UserConstants.KEY_NAME, "");
			User user = new User(uid, login, fullName, password);
			String email = userProfileNode.get(UserConstants.KEY_EMAIL, "");
			if (!email.equals("")) {
				user.setEmail(email);
			}
			String blocked = userProfileNode.get(UserConstants.KEY_BLOCKED, "false");
			if (blocked.equals("true")) {
				user.setBlocked(true);
			}
			String emailConfirmation = userProfileNode.get(UserConstants.KEY_EMAIL_CONFIRMATION, "false");
			if (emailConfirmation.equals("true")) {
				user.setConfirmationId(emailConfirmation);
			}
			String[] keys = userProfileNode.getUserProfileNode(USER_PROPERTIES).keys();
			if (keys.length > 0) {
				for (int i = 0; i < keys.length; i++) {
					String key = keys[i];
					String value = userProfileNode.getUserProfileNode(USER_PROPERTIES).get(key, "");
					user.addProperty(key, value);
				}
			}

			return user;
		} catch (CoreException e) {
			LogHelper.log(e);
		}
		return null;
	}

	public Authorization getAuthorization(User user) {
		if (user instanceof User) {
			return new WebIdeAuthorization((User) user);
		}
		return new EmptyAuthorization();
	}

	public Set<User> getUsersByProperty(String key, String value, boolean regExp, boolean ignoreCase) {
		Set<User> ret = new HashSet<User>();
		Pattern p = regExp ? Pattern.compile(value, Pattern.MULTILINE | Pattern.DOTALL) : null;

		if (key.equals("openid")) {
			// Use the openid cache to lookup the user for the openid property
			for (Map.Entry<String, String> entry : openidCache.entrySet()) {
				String openid = entry.getKey();
				String uid = entry.getValue();
				boolean hasMatch;
				if (p != null) {
					hasMatch = p.matcher(openid).matches();
				} else {
					hasMatch = ignoreCase ? openid.equalsIgnoreCase(value) : openid.equals(value);
				}
				if (hasMatch) {
					IOrionUserProfileNode userNode = root.getUserProfileNode(uid);
					ret.add(formUser(userNode));
				}
			}
		} else {
			for (String uid : root.childrenNames()) {
				IOrionUserProfileNode userNode = root.getUserProfileNode(uid);
				IOrionUserProfileNode propsNode = userNode.getUserProfileNode(USER_PROPERTIES);
				if (propsNode == null) {
					continue;
				}
				try {
					String propertyValue = propsNode.get(key, null);
					if (propertyValue == null) {
						continue;
					}
					boolean hasMatch;
					if (p != null) {
						hasMatch = p.matcher(propertyValue).matches();
					} else {
						hasMatch = ignoreCase ? propertyValue.equalsIgnoreCase(value) : propertyValue.equals(value);
					}
					if (hasMatch) {
						ret.add(formUser(userNode));
					}

				} catch (CoreException e) {
					LogHelper.log(e);
				}
			}
		}

		return ret;
	}

}
