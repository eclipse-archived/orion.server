/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.JSONSerializer;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.core.users.UserUtilities;
import org.eclipse.orion.server.git.BaseToCommitConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Blame.TYPE)
public class Blame extends GitObject {

	public static final String RESOURCE = "blame"; //$NON-NLS-1$

	public static final String TYPE = "Blame"; //$NON-NLS-1$

	private List<String> lines = new ArrayList<String>();

	private List<RevCommit> commits = new ArrayList<RevCommit>();

	private String filePath = null;

	private ObjectId startCommit = null;

	protected JSONSerializer jsonSerializer = new JSONSerializer();

	private URI blameLocation = null;

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_LOCATION), // super
				new Property(GitConstants.KEY_CLONE), // super
				new Property(ProtocolConstants.KEY_CHILDREN) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	public Blame(URI cloneLocation, Repository db) {
		super(cloneLocation, db);
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
	 * Set the file path which will be blamed
	 * 
	 * @param path
	 */
	public void setFilePath(String path) {
		this.filePath = path;
	}

	/**
	 * Add RevCommit to needed commits
	 * 
	 * @param commit
	 */
	public void addCommit(RevCommit commit) {
		this.commits.add(commit);
	}

	/**
	 * Check is commit is in the list
	 * 
	 * @param commit
	 * @return boolean if exists
	 */
	public boolean commitExists(RevCommit commit) {
		return this.commits.contains(commit);
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
	 * clear lines array
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
	 * return the commit the blame function will start from
	 * 
	 * @return ObjectId
	 */
	public ObjectId getStartCommit() {
		return this.startCommit;
	}

	/**
	 * Set the URI for the blame for this file
	 * 
	 * @param location
	 */
	public void setBlameLocation(URI location) {
		this.blameLocation = location;
	}

	/**
	 * JSON Serializing
	 */

	@Override
	public JSONObject toJSON() throws URISyntaxException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return blameLocation;
	}

	@PropertyDescription(name = ProtocolConstants.KEY_CHILDREN)
	private JSONArray getBlameJSON() throws URISyntaxException, JSONException, IOException {
		if (lines.size() > 0 && commits.size() > 0) {
			ArrayList<JSONArray> commitRanges = new ArrayList<JSONArray>();
			JSONObject tempObj = null;
			String lineId = null;
			String currentCommitId = null;
			for (int i = 0; i < commits.size(); i++) {
				commitRanges.add(new JSONArray());
			}
			try {
				for (int i = 0; i < lines.size(); i++) {
					lineId = lines.get(i);
					if (lineId != null && !lineId.equals(currentCommitId)) {
						if (tempObj != null) {
							tempObj.put(GitConstants.KEY_END_RANGE, i);
							for (int j = 0; j < commits.size(); j++) {
								if (commits.get(j).getId().getName().equals(currentCommitId)) {
									commitRanges.get(j).put(tempObj);
									break;
								}
							}

						}
						tempObj = new JSONObject();
						tempObj.put(GitConstants.KEY_START_RANGE, i + 1);
						currentCommitId = lineId;
					}
				}
				tempObj.put(GitConstants.KEY_END_RANGE, lines.size());
				for (int j = 0; j < commits.size(); j++) {
					if (commits.get(j).getId().getName().equals(lineId)) {
						commitRanges.get(j).put(tempObj);
						break;
					}
				}
			} catch (NullPointerException e) {
				e.printStackTrace();
			}
			JSONArray returnJSON = new JSONArray();
			RevCommit tempCommit;
			PersonIdent person;
			for (int i = 0; i < commitRanges.size(); i++) {
				tempObj = new JSONObject();
				tempCommit = commits.get(i);
				person = tempCommit.getAuthorIdent();
				URI commitURI = BaseToCommitConverter.getCommitLocation(cloneLocation, tempCommit.getId().getName(), BaseToCommitConverter.REMOVE_FIRST_2);
				tempObj = new JSONObject();
				tempObj.put(GitConstants.KEY_COMMIT_TIME, (long) tempCommit.getCommitTime() * 1000);
				tempObj.put(GitConstants.KEY_AUTHOR_EMAIL, person.getEmailAddress());
				tempObj.put(GitConstants.KEY_AUTHOR_NAME, person.getName());
				tempObj.put(GitConstants.KEY_AUTHOR_IMAGE, UserUtilities.getImageLink(person.getEmailAddress()));
				person = tempCommit.getCommitterIdent();
				tempObj.put(GitConstants.KEY_COMMITTER_EMAIL, person.getEmailAddress());
				tempObj.put(GitConstants.KEY_COMMITTER_NAME, person.getName());
				tempObj.put(GitConstants.KEY_COMMIT, commitURI);
				tempObj.put(ProtocolConstants.KEY_CHILDREN, commitRanges.get(i));
				tempObj.put(GitConstants.KEY_COMMIT_MESSAGE, tempCommit.getFullMessage());
				tempObj.put(ProtocolConstants.KEY_NAME, tempCommit.getId().getName());
				returnJSON.put(tempObj);
			}
			return returnJSON;

		}
		return null;
	}

}
