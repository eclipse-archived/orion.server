/*******************************************************************************
 * Copyright (c) 2014, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.core.metastore;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.ServerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cache for various user properties in a {@code SimpleMetaStore}. It is expensive to go to disk 
 * to search for users matching a particular property, such as oauth or email address.
 * This user property cache is used by the {@link SimpleMetaStore#registerUserProperty(String) registerUserProperty} 
 * and {@link SimpleMetaStore#readUserByProperty(String, String, boolean, boolean) readUserByProperty} to quickly
 * locate users.
 * 
 * @author Anthony Hunter
 */
public class SimpleMetaStoreUserPropertyCache {
	Logger logger = LoggerFactory.getLogger(SimpleMetaStoreUserPropertyCache.class); //$NON-NLS-1$

	/**
	 * A map of the user caches keyed by the property key.
	 */
	private Map<String, Map<String, String>> cacheMap = new ConcurrentHashMap<String, Map<String, String>>();

	public SimpleMetaStoreUserPropertyCache() {
	}

	public void register(List<String> propertyKeys) throws CoreException {
		for (String propertyKey : propertyKeys) {
			if (cacheMap.containsKey(propertyKey)) {
				throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStoreUserPropertyCache.registerUserProperty: property " + propertyKey + " is already registered", null));
			}
			Map<String, String> cache = new ConcurrentHashMap<String, String>();
			cacheMap.put(propertyKey, cache);
			if (logger.isDebugEnabled()) {
				logger.debug("Created user cache for the " + propertyKey + " property"); //$NON-NLS-1$
			}
		}
	}

	public boolean isRegistered(String propertyKey) {
		return cacheMap.containsKey(propertyKey);
	}

	public void add(String propertyKey, String value, String userId) {
		Map<String, String> cache = cacheMap.get(propertyKey);
		if (cache == null) {
			return;
		}
		if (cache.containsValue(userId)) {
			String key = null;
			for (Entry<String, String> entry : cache.entrySet()) {
				if (userId.equals(entry.getValue())) {
					key = entry.getKey();
				}
			}
			if (key != null) {
				cache.remove(key);
				if (logger.isDebugEnabled()) {
					logger.debug("Removed " + key + " for the user " + userId + " from the user cache for the " + propertyKey + " property"); //$NON-NLS-1$
				}
			}
		}
		cache.put(value, userId);
		if (logger.isDebugEnabled()) {
			logger.debug("Added " + value + " for the user " + userId + " to the user cache for the " + propertyKey + " property"); //$NON-NLS-1$
		}
	}

	public void deleteUser(String userId) {
		for (String propertyKey : cacheMap.keySet()) {
			Map<String, String> cache = cacheMap.get(propertyKey);
			if (cache.containsValue(userId)) {
				String key = null;
				for (Entry<String, String> entry : cache.entrySet()) {
					if (userId.equals(entry.getValue())) {
						key = entry.getKey();
					}
				}
				if (key != null) {
					cache.remove(key);
					if (logger.isDebugEnabled()) {
						logger.debug("Removed " + key + " for the user " + userId + " from the user cache for the " + propertyKey + " property"); //$NON-NLS-1$
					}
				}
			}
		}
	}

	public String readUserByProperty(String key, String value, boolean regExp, boolean ignoreCase) throws CoreException {
		if (!cacheMap.containsKey(key)) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "SimpleMetaStore.registerUserProperty: property " + key + " is not registered", null));
		}
		Map<String, String> cache = cacheMap.get(key);
		Pattern pattern = regExp ? Pattern.compile(value, Pattern.MULTILINE | Pattern.DOTALL) : null;

		// Use the cache to lookup the user for the specified property value
		for (Map.Entry<String, String> entry : cache.entrySet()) {
			String cacheKey = entry.getKey();
			String userId = entry.getValue();
			boolean hasMatch;
			if (pattern != null) {
				hasMatch = pattern.matcher(cacheKey).matches();
			} else {
				hasMatch = ignoreCase ? cacheKey.equalsIgnoreCase(value) : cacheKey.equals(value);
			}
			if (hasMatch) {
				return userId;
			}
		}
		return null;
	}

	public void setProperties(String userId, Map<String, String> properties) {
		for (String key : properties.keySet()) {
			String value = properties.get(key);
			if (cacheMap.containsKey(key)) {
				Map<String, String> cache = cacheMap.get(key);
				if (cache.containsValue(userId)) {
					String oldValue = null;
					for (Entry<String, String> entry : cache.entrySet()) {
						if (userId.equals(entry.getValue())) {
							oldValue = entry.getKey();
						}
					}
					if (oldValue != null) {
						cache.remove(oldValue);
						if (logger.isDebugEnabled()) {
							logger.debug("Removed " + oldValue + " for the user " + userId + " to the user cache for the " + key + " property"); //$NON-NLS-1$
						}
					}
				}
				cache.put(value, userId);
				if (logger.isDebugEnabled()) {
					logger.debug("Added " + value + " for the user " + userId + " to the user cache for the " + key + " property"); //$NON-NLS-1$
				}
			}
		}
	}

	public void delete(String key, String value, String userId) {
		Map<String, String> cache = cacheMap.get(key);
		cache.remove(value);
		if (logger.isDebugEnabled()) {
			logger.debug("Deleted " + value + " for the user " + userId + " from the user cache for the " + key + " property"); //$NON-NLS-1$
		}
	}
}
