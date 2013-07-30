/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
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
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.server.core.resources.*;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.BaseToCommitConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.json.*;

@ResourceDescription(type = Blame.TYPE)
public class Blame extends GitObject {

	public static final String RESOURCE = "blame"; //$NON-NLS-1$

	public static final String TYPE = "Blame"; //$NON-NLS-1$

	private Repository db = null;

	private List<String> lines = new ArrayList<String>();

	private String filePath = null;

	private ObjectId startCommit = null;

	protected Serializer<JSONObject> jsonSerializer = new JSONSerializer();

	private URI cloneLocation;

	private String blameLocation = null;

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] {new Property(GitConstants.KEY_BLAME_LOCATION), new Property(GitConstants.KEY_BLAME_INFO)};
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	public Blame(URI cloneLocation, Repository db) {
		super(cloneLocation, db);
		this.setRepository(db);
		this.setCloneLocation(cloneLocation);
	}

	/*
	 * Getters and Setters
	 */

	/**
	 * Add Strings of commitIds for each line
	 * 
	 * @param line
	 */
	public void addLine(String line) {
		this.lines.add(line);
	}

	/**
	 * Returns a list of commitIds that correspond to each line in a file
	 * 
	 * @return lines list
	 */
	public List<String> getLines() {
		return this.lines;
	}

	/**
	 * Get a specific commit Id for a line
	 * 
	 * @param index
	 * @return String of list element
	 * 
	 */
	public String getLine(int index) {
		return this.lines.get(index);
	}

	/**
	 * 
	 * @param repo
	 */
	public void setRepository(Repository repo) {
		this.db = repo;
	}

	/**
	 * 
	 * @return repository
	 */
	public Repository getRepository() {
		return this.db;
	}

	/**
	 * Set the file path which will be blamed
	 * 
	 * @param path
	 */
	public void setFilePath(String path) {
		this.filePath = path;
	}

	/**
	 * Get the file path for the blame object
	 * 
	 * @return file path of blame object
	 */
	public String getFilePath() {
		return this.filePath;
	}

	/**
	 *  clear lines array
	 */
	public void clearLines() {
		this.lines = new ArrayList<String>();
	}

	/**
	 * Set the Commit where the blameing will start from
	 */

	public void setStartCommit(ObjectId id) {
		this.startCommit = id;
	}

	/**
	 *  return the commit the blame function will start from
	 * 
	 * @return ObjectId
	 */
	public ObjectId getStartCommit() {
		return this.startCommit;
	}

	/**
	 * Set cloneLocation for BaseToCommitConverter
	 */

	public void setCloneLocation(URI uri) {
		cloneLocation = uri;
	}

	/**
	 * Get cloneLocation for BaseToCommitConverter
	 * 
	 * @return URI
	 */
	public URI getCloneLocation() {
		return cloneLocation;
	}

	/**
	 * Set the URI for the blame for this file
	 * @param location
	 */
	public void setBlameLocation(String location) {
		this.blameLocation = location;
	}

	/**
	 * Returns the URI of the blame location
	 * @return blameLocation
	 */
	public String getBlameLocation() {
		return this.blameLocation;
	}

	/**
	 * JSON Serializing
	 */

	public JSONObject toJSON() throws URISyntaxException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@PropertyDescription(name = GitConstants.KEY_BLAME_LOCATION)
	private String getLocatJSON() throws JSONException {
		if (this.getBlameLocation() != null) {
			return this.getBlameLocation();
		}
		return null;
	}

	@PropertyDescription(name = GitConstants.KEY_BLAME_INFO)
	private JSONArray getBlameJSON() throws URISyntaxException, JSONException, IOException {
		JSONArray LinesJSON = new JSONArray();
		if (lines.size() > 0) {

			JSONObject tempObj = null;
			String tempPath = null;
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (!line.equalsIgnoreCase(tempPath)) {
					tempPath = line;
					if (i > 0) {
						tempObj.put(GitConstants.KEY_END_RANGE, i);
						LinesJSON.put(tempObj);
					}
					tempObj = new JSONObject();
					tempObj.put(GitConstants.KEY_START_RANGE, i + 1);
					URI commit = BaseToCommitConverter.getCommitLocation(cloneLocation, line, BaseToCommitConverter.REMOVE_FIRST_2);
					tempObj.put(GitConstants.KEY_COMMIT, commit);
					tempObj.put(GitConstants.KEY_OBJECTID, line);
				}

			}
			tempObj.put(GitConstants.KEY_END_RANGE, lines.size());
			LinesJSON.put(tempObj);

			return LinesJSON;
		}

		return null;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 *
	 * JSON STRUCTURE
	 * {
	 * 	"Location": "file.java",
	 *  "Type": "Blame"
	 *  "Blame":
	 *  {
	 * 	 	{
	 *     		"CommitLocation":"/gitapi/commit/2b3a36c1b2f0064216a871740bd6906b6af7434a/file/C/",
	 *     		"Start": 0,
	 *     		"End": 7
	 *   	},
	 *   	{
	 *     		"CommitLocation":"/gitapi/commit/fb774ffe5b1bbfbe80ac9afb5263901afdc12874/file/C/",
	 *     		"Start": 8,
	 *     		"End": 13
	 *   	{
	 *   }
	 *   
	 */

}
