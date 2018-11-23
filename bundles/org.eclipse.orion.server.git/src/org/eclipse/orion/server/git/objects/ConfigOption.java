/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.objects;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.BaseToConfigEntryConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = ConfigOption.TYPE)
@SuppressWarnings("restriction")
public class ConfigOption extends GitObject {

	public static final String RESOURCE = "config"; //$NON-NLS-1$
	public static final String TYPE = "Config"; //$NON-NLS-1$

	private static final String EMPTY_VALUE = ""; //$NON-NLS-1$

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	private static final ResourceShape DEFAULT_RESOURCE_SHAPE_COLLECTION = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_LOCATION), // super
				new Property(GitConstants.KEY_CLONE), // super
				new Property(GitConstants.KEY_CONFIG_ENTRY_KEY), //
				new Property(GitConstants.KEY_CONFIG_ENTRY_VALUE) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);

		Property childrenProperty = new Property(ProtocolConstants.KEY_CHILDREN);
		childrenProperty.setResourceShape(DEFAULT_RESOURCE_SHAPE);
		Property[] collectionProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_LOCATION), // super
				new Property(GitConstants.KEY_CLONE), // super
				childrenProperty };
		DEFAULT_RESOURCE_SHAPE_COLLECTION.setProperties(collectionProperties);
	}

	private FileBasedConfig config;
	private String[] keySegments;

	public ConfigOption(URI cloneLocation, Repository db) throws IOException {
		super(cloneLocation, db);
		this.config = getLocalConfig();
	}

	public ConfigOption(URI cloneLocation, Repository db, String key) throws IOException {
		this(cloneLocation, db);
		key = GitUtils.decode(key);
		this.keySegments = keyToSegments(key);
		Assert.isLegal(this.keySegments != null, "Config entry key must be provided in the following form: section[.subsection].name");
	}

	private ConfigOption(URI cloneLocation, Repository db, String[] keySegments) throws IOException {
		this(cloneLocation, db);
		this.keySegments = keySegments;
	}

	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		if (keySegments == null)
			return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE_COLLECTION);
		else
			return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_CHILDREN)
	private JSONArray getChildren() throws JSONException, URISyntaxException, IOException, CoreException {
		JSONArray children = new JSONArray();
		for (String section : config.getSections()) {
			// proceed configuration entries: section.name
			for (String name : config.getNames(section))
				children.put(new ConfigOption(cloneLocation, db, new String[] { section, null, name }).toJSON());
			// proceed configuration entries: section.subsection.name
			for (String subsection : config.getSubsections(section))
				for (String name : config.getNames(section, subsection))
					children.put(new ConfigOption(cloneLocation, db, new String[] { section, subsection, name }).toJSON());
		}
		return children;
	}

	@PropertyDescription(name = GitConstants.KEY_CONFIG_ENTRY_KEY)
	private String getKey() {
		return segmentsToKey(keySegments);
	}

	@PropertyDescription(name = GitConstants.KEY_CONFIG_ENTRY_VALUE)
	public String[] getValue() {
		String[] value = config.getStringList(keySegments[0], keySegments[1], keySegments[2]);
		if (value == null)
			value = null;
		return value;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		String key = keySegments != null ? segmentsToKey(keySegments) : ""; //$NON-NLS-1$
		return BaseToConfigEntryConverter.CLONE.baseToConfigEntryLocation(cloneLocation, GitUtils.encode(key));
	}

	/**
	 * Retrieves local config without any base config.
	 */
	private FileBasedConfig getLocalConfig() throws IOException {
		// TODO: remove usage of internal type
		if (db instanceof FileRepository) {
			FileRepository fr = (FileRepository) db;
			FileBasedConfig config = new FileBasedConfig(fr.getConfig().getFile(), FS.detect());
			try {
				config.load();
			} catch (ConfigInvalidException e) {
				throw new IOException(e);
			}
			return config;
		} else {
			throw new IllegalArgumentException("Repository is not file based.");
		}
	}

	/**
	 * Converts array of the key segments to the string representation.
	 * 
	 * @param segments
	 *            array containing three elements: section, subsection and name
	 * @return string representation of the key or <code>null</code> if input array is invalid
	 */
	private String segmentsToKey(String[] segments) {
		if (segments.length == 3)
			// check if there is subsection part
			return segments[1] == null ? String.format("%s.%s", segments[0], segments[2]) : String.format("%s.%s.%s", segments[0], segments[1], segments[2]); //$NON-NLS-1$ //$NON-NLS-2$
		return null;
	}

	/**
	 * Converts the string key representation to the key segments array.
	 * 
	 * @param string
	 *            key representation, expected format: section[.subsection].name
	 * @return array containing segments of keys or <code>null</code> if key is invalid
	 */
	private String[] keyToSegments(String key) {
		int firstDot = key.indexOf('.');
		int lastDot = key.lastIndexOf('.');
		// we expect at least one dot character
		if (firstDot == -1 || lastDot == -1)
			return null;

		// section is required
		String section = key.substring(0, firstDot);
		// subsection is optional
		String subsection = null;
		if (firstDot != lastDot)
			subsection = key.substring(firstDot + 1, lastDot);
		// name is required
		String name = key.substring(lastDot + 1);

		return new String[] { section, subsection, name };
	}

	/**
	 * Checks if given variable exist in configuration.
	 */
	public boolean exists() {
		if (keySegments[1] != null && !config.getNames(keySegments[0], keySegments[1]).contains(keySegments[2]))
			return false;
		else if (keySegments[1] == null && !config.getNames(keySegments[0]).contains(keySegments[2]))
			return false;
		return true;
	}

	@Override
	public FileBasedConfig getConfig() {
		return config;
	}

	public String getSection() {
		return keySegments[0];
	}

	public String getSubsection() {
		return keySegments[1];
	}

	public String getName() {
		return keySegments[2];
	}
}
