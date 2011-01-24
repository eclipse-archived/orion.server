/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets;

/**
 * Constants used by the eclipse web HTTP protocol.
 */
public class ProtocolConstants {

	/**
	 * Standard HTTP request or response value for the Content-Type header,
	 * indicating that the request or response body consists of a generic JSON object.
	 */
	public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";//$NON-NLS-1$

	/**
	 * Standard HTTP request or response header indicating the content length of the request
	 * or response body.
	 */
	public static final String HEADER_CONTENT_LENGTH = "Content-Length"; //$NON-NLS-1$

	/**
	 * Standard HTTP request or response header indicating the content range of the request
	 * or response body.
	 */
	public static final String HEADER_CONTENT_RANGE = "Content-Range"; //$NON-NLS-1$

	/**
	 * Standard HTTP request or response header indicating the content type of the request
	 * or response body.
	 */
	public static final String HEADER_CONTENT_TYPE = "Content-Type"; //$NON-NLS-1$

	/**
	 * Non-standard HTTP request header indicating the options to use when creating
	 * a resource during post.
	 */
	public static final String HEADER_CREATE_OPTIONS = "X-Create-Options"; //$NON-NLS-1$

	/**
	 * Standard HTTP response header indicating location of the created resource.
	 */
	public static final String HEADER_LOCATION = "Location"; //$NON-NLS-1$

	/**
	 * Common HTTP request header indicating the suggested name of the new resource
	 * to be created by a POST operation.
	 */
	public static final String HEADER_SLUG = "Slug"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's children. The value's data
	 * type is a JSON array of workspace objects.
	 */
	public static final String KEY_CHILDREN = "Children"; //$NON-NLS-1$

	/**
	 * JSON representation key for the server location of an object's children. Performing
	 * a GET on this location should return an object containing children objects.
	 * The value's data type is a String.
	 */
	public static final String KEY_CHILDREN_LOCATION = "ChildrenLocation"; //$NON-NLS-1$

	/**
	 * JSON representation key for the location of an object's contents. This key
	 * typically only exists when an object has both metadata and non-metadata content.
	 * In this case {@link #KEY_LOCATION} refers to the object's metadata, and this
	 * key refers to the non-metadata content.
	 * The value's data type is a String.
	 */
	public static final String KEY_CONTENT_LOCATION = "ContentLocation"; //$NON-NLS-1$

	/**
	 * JSON representation key for whether a file is a directory or not. The value's data
	 * type is a boolean.
	 */
	public static final String KEY_DIRECTORY = "Directory"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's id. The value's data type is a String.
	 */
	public static final String KEY_ID = "Id"; //$NON-NLS-1$
	/**
	 * JSON representation key for an object's last modified time. The value's
	 * data type is 'long' and represents the number of milliseconds since
	 * 00:00:00 UTC on January 1, 1970.
	 */
	public static final String KEY_LAST_MODIFIED = "LastModified"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's length. The value's data type is 'long'.
	 */
	public static final String KEY_LENGTH = "Length"; //$NON-NLS-1$

	/**
	 * JSON representation key for a file's local time stamp. The value's
	 * data type is 'long' and represents the number of milliseconds since
	 * 00:00:00 UTC on January 1, 1970.
	 */
	public static final String KEY_LOCAL_TIMESTAMP = "LocalTimeStamp"; //$NON-NLS-1$

	/**
	 * JSON representation key for the location of an object.
	 * The value's data type is a String.
	 */
	public static final String KEY_LOCATION = "Location"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's name. The value's data type is a String
	 */
	public static final String KEY_NAME = "Name"; //$NON-NLS-1$

	public static final String KEY_CREATE_IF_DOESNT_EXIST = "CreateIfDoesntExist"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's parents. The value's data
	 * type is a JSON array of objects with name and location values.
	 */
	public static final String KEY_PARENTS = "Parents"; //$NON-NLS-1$

	/**
	 * JSON representation key for a workspace's list of projects. The value's data
	 * type is a JSON array of workspace objects.
	 */
	public static final String KEY_PROJECTS = "Projects"; //$NON-NLS-1$

	/**
	 * JSON representation key for the location of an object's search service.
	 * The value's data type is String.
	 */
	public static final String KEY_SEARCH_LOCATION = "SearchLocation"; //$NON-NLS-1$

	/**
	 * JSON representation key for the location of an object's import service.
	 * The value's data type is String.
	 */
	public static final String KEY_IMPORT_LOCATION = "ImportLocation"; //$NON-NLS-1$

	/**
	 * JSON representation key for the location of an object's export service.
	 * The value's data type is String.
	 */
	public static final String KEY_EXPORT_LOCATION = "ExportLocation"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's name. The value's data type is a String
	 */
	public static final String KEY_USER_NAME = "UserName"; //$NON-NLS-1$

	/**
	 * JSON representation key for user's rights. The value's data
	 * type is a JSON array of Strings.
	 */
	public static final String KEY_USER_RIGHTS = "UserRights"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's list of workspaces. The value's data
	 * type is a JSON array of workspace objects.
	 */
	public static final String KEY_WORKSPACES = "Workspaces"; //$NON-NLS-1$

	/**
	 * Query parameter on HTTP requests for directories, indicating the depth
	 * of children to be encoded in the response.
	 */
	public static final String PARM_DEPTH = "depth"; //$NON-NLS-1$

	/**
	 * HTTP request header, indicating the length of a file to be transferred.
	 */
	public static final String HEADER_XFER_LENGTH = "X-Xfer-Content-Length"; //$NON-NLS-1$

	/**
	 * HTTP request header, indicating options for an import operation.
	 */
	public static final String HEADER_XFER_OPTIONS = "X-Xfer-Options"; //$NON-NLS-1$

}
