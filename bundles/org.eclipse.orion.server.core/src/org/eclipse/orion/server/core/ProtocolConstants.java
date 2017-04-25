/*******************************************************************************
 * Copyright (c) 2010, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core;

/**
 * Constants used by the Orion HTTP protocol.
 */
public class ProtocolConstants {

	/**
	 * Standard HTTP request or response value for the Content-Type header,
	 * indicating that the request or response body consists of css content.
	 */
	public static final String CONTENT_TYPE_CSS = "application/css";//$NON-NLS-1$

	/**
	 * Standard HTTP request or response value for the Content-Type header,
	 * indicating that the request or response body consists of a font.
	 */
	public static final String CONTENT_TYPE_FONT = "application/font-woff";//$NON-NLS-1$

	/**
	 * Standard HTTP request or response value for the Content-Type header,
	 * indicating that the request or response body consists of HTML.
	 */
	public static final String CONTENT_TYPE_HTML = "text/html;charset=UTF-8";//$NON-NLS-1$

	/**
	 * Standard HTTP request or response value for the Content-Type header,
	 * indicating that the request or response body consists of javascript.
	 */
	public static final String CONTENT_TYPE_JAVASCRIPT = "application/x-javascript; charset=UTF-8";//$NON-NLS-1$

	/**
	 * Standard HTTP request or response value for the Content-Type header,
	 * indicating that the request or response body consists of a generic JSON object.
	 */
	public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";//$NON-NLS-1$

	/**
	 * Standard HTTP request or response value for the Content-Type header,
	 * indicating that the request or response body consists of a generic JSON patch object.
	 */
	public static final String CONTENT_TYPE_JSON_PATCH = "application/json-patch; charset=UTF-8";//$NON-NLS-1$

	/**
	 * Standard HTTP request or response value for the Content-Type header,
	 * indicating that the request or response body consists of plain text.
	 */
	public static final String CONTENT_TYPE_PLAIN_TEXT = "text/plain;charset=UTF-8";//$NON-NLS-1$

	/**
	 * Standard HTTP request or response header indicating what kind of patch a server
	 * supports.
	 */
	public static final String HEADER_ACCEPT_PATCH = "Accept-Patch"; //$NON-NLS-1$

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
	 * Non-standard HTTP request header indicating a different method overriding the current
	 * operation.
	 */
	public static final String HEADER_METHOD_OVERRIDE = "X-HTTP-Method-Override"; //$NON-NLS-1$

	/**
	 * Standard HTTP request header indicating that operation shall be executed if the entity tag matches the resource representation.
	 */
	public static final String HEADER_IF_MATCH = "If-Match"; //$NON-NLS-1$

	/**
	 * Standard HTTP request header indicating that operation must not be executed if the entity tag matches the resource  representation.
	 */
	public static final String HEADER_IF_NONE_MATCH = "If-None-Match"; //$NON-NLS-1$

	/**
	 * Standard HTTP response header indicating location of the created resource.
	 */
	public static final String HEADER_LOCATION = "Location"; //$NON-NLS-1$

	/**
	 * HTTP request header, indicating the orion server API version in use.
	 */
	public static final String HEADER_ORION_VERSION = "Orion-Version"; //$NON-NLS-1$

	/**
	 * Common HTTP request header indicating the suggested name of the new resource
	 * to be created by a POST operation.
	 */
	public static final String HEADER_SLUG = "Slug"; //$NON-NLS-1$

	/**
	 * HTTP request header, indicating the length of a file to be transferred.
	 */
	public static final String HEADER_XFER_LENGTH = "X-Xfer-Content-Length"; //$NON-NLS-1$

	/**
	 * HTTP request header, indicating options for an import operation.
	 */
	public static final String HEADER_XFER_OPTIONS = "X-Xfer-Options"; //$NON-NLS-1$

	/**
	 * HTTP response header, indicating an authentication challenge (rfc 2617)
	 */
	public static final String HEADER_WWW_AUTHENTICATE = "WWW-Authenticate"; //$NON-NLS-1$

	/**
	 * Option header value indicating that this is a copy request.
	 */
	public static final String OPTION_COPY = "copy"; //$NON-NLS-1$

	/**
	 * Option header value indicating that this is a move request.
	 */
	public static final String OPTION_MOVE = "move"; //$NON-NLS-1$

	/**
	 * Option header value indicating that no overwrite should occur if the destination
	 * resource already exists. An attempt to replace an existing destination resource will
	 * fail if this option is specified.
	 */
	public static final String OPTION_NO_OVERWRITE = "no-overwrite"; //$NON-NLS-1$

	/**
	 * Option header value indicating that overwrite should only occur if the destination
	 * resource is older than the resource to be added.
	 */
	public static final String OPTION_OVERWRITE_OLDER = "overwrite-older"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's children. The value's data
	 * type is a JSON array of workspace objects.
	 */
	public static final String KEY_CHILDREN = "Children"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's children ids that have been deleted. The value's data
	 * type is a JSON array of workspace objects.
	 */
	public static final String KEY_DELETED_CHILDREN = "DeletedChildren"; //$NON-NLS-1$

	/**
	 * JSON representation key for the server location of an object's children. Performing
	 * a GET on this location should return an object containing children objects.
	 * The value's data type is a String.
	 */
	public static final String KEY_CHILDREN_LOCATION = "ChildrenLocation"; //$NON-NLS-1$

	/**
	 * JSON representation of Project information. Should contain at least Location.
	 */
	public static final String KEY_PROJECT_INFO = "ProjectInfo"; //$NON-NLS-1$

	/**
	 * JSON representation key for the location of an object's contents. This key
	 * typically only exists when an object has both metadata and non-metadata content.
	 * In this case {@link #KEY_LOCATION} refers to the object's metadata, and this
	 * key refers to the non-metadata content.
	 * The value's data type is a String.
	 */
	public static final String KEY_CONTENT_LOCATION = "ContentLocation"; //$NON-NLS-1$

	public static final String KEY_CREATE_IF_DOESNT_EXIST = "CreateIfDoesntExist"; //$NON-NLS-1$

	/**
	 * JSON representation key for whether a file is a directory or not. The value's data
	 * type is a boolean.
	 */
	public static final String KEY_DIRECTORY = "Directory"; //$NON-NLS-1$

	/**
	 * JSON representation key for the location of an object's export service.
	 * The value's data type is String.
	 */
	public static final String KEY_EXPORT_LOCATION = "ExportLocation"; //$NON-NLS-1$

	/**
	 * JSON representation key indicating what files should be excluded in Zip export.
	 * This key will be removed by TransferResourceDecorator when the correct {@link #KEY_EXPORT_LOCATION} will be added
	 */
	public static final String KEY_EXCLUDED_IN_EXPORT = "excludedInExport"; //$NON-NLS-1$

	/**
	 * JSON representation key indicating what files should be excluded in Zip import.
	 * This key will be removed by TransferResourceDecorator when the correct {@link #KEY_IMPORT_LOCATION} will be added
	 */
	public static final String KEY_EXCLUDED_IN_IMPORT = "excludedInImport"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's id. The value's data type is a String.
	 */
	public static final String KEY_ID = "Id"; //$NON-NLS-1$

	/**
	 * JSON representation key for the location of an object's import service.
	 * The value's data type is String.
	 */
	public static final String KEY_IMPORT_LOCATION = "ImportLocation"; //$NON-NLS-1$

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
	 * JSON representation key for the workspace location of an object.
	 * The value's data type is a String.
	 */
	public static final String KEY_WORKSPACE_LOCATION = "WorkspaceLocation"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's name. The value's data type is a String.
	 */
	public static final String KEY_NAME = "Name"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's name in lower case. The value's data type is a String.
	 */
	public static final String KEY_NAME_LOWERCASE = "NameLower"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's full name. The value's data type is a String.
	 */
	public static final String KEY_FULL_NAME = "FullName"; //$NON-NLS-1$

	/**
	 * JSON representation key for an object's type. The value's data type is a String.
	 */
	public static final String KEY_TYPE = "Type"; //$NON-NLS-1$

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
	 * JSON representation key for an object's name. The value's data type is a String
	 */
	public static final String KEY_USER_NAME = "UserName"; //$NON-NLS-1$

	/**
	 * JSON representation key for user's rights. The value's data
	 * type is a integer indicating the supported HTTP methods.
	 */
	public static final String KEY_USER_RIGHT_METHOD = "Method"; //$NON-NLS-1$

	/**
	 * JSON representation key for user's rights. The value's data
	 * type is a String indicating the URI prefix the user has access to.
	 */
	public static final String KEY_USER_RIGHT_URI = "Uri"; //$NON-NLS-1$

	/**
	 * JSON representation key for user's rights. The value's data
	 * type is a JSON array of JSON objects.
	 */
	public static final String KEY_USER_RIGHTS = "UserRights"; //$NON-NLS-1$

	/**
	 * JSON representation key for the version of user rights data. The value's
	 * data type is an Integer.
	 */
	public static final String KEY_USER_RIGHTS_VERSION = "UserRightsVersion"; //$NON-NLS-1$

	/**
	 * JSON representation key for a user's list of workspaces. The value's data
	 * type is a JSON array of workspace objects.
	 */
	public static final String KEY_WORKSPACES = "Workspaces"; //$NON-NLS-1$

	/**
	 * Query parameter on HTTP requests for exporting files in Zip format.
	 * It should contain the comma separated list of files that should be excluded in zip export.
	 * It should be added to {@link #KEY_EXPORT_LOCATION}
	 */
	public static final String PARAM_EXCLUDE = "exclude"; //$NON-NLS-1$

	/**
	 * Query parameter on HTTP requests for directories, indicating the depth
	 * of children to be encoded in the response.
	 */
	public static final String PARM_DEPTH = "depth"; //$NON-NLS-1$

	/**
	 * Query parameter on HTTP requests for files, indicating the source
	 * of the content to be written.
	 */
	public static final String PARM_SOURCE = "source"; //$NON-NLS-1$

	/**
	 * JSON representation key for a file's attributes. The value's data
	 * type is a JSON object of String/Boolean pairs.
	 */
	public static final String KEY_ATTRIBUTES = "Attributes"; //$NON-NLS-1$
	/**
	 * JSON representation key for a file's read only attribute. The value's data
	 * type is a Boolean.
	 */
	public static final String KEY_ATTRIBUTE_READ_ONLY = "ReadOnly"; //$NON-NLS-1$
	/**
	 * JSON representation key for a file's execute attribute. The value's data
	 * type is a Boolean.
	 */
	public static final String KEY_ATTRIBUTE_EXECUTABLE = "Executable"; //$NON-NLS-1$

	/**
	 * JSON representation key for a file's immutable attribute. The value's data
	 * type is a Boolean.
	 */
	public static final String KEY_ATTRIBUTE_IMMUTABLE = "Immutable"; //$NON-NLS-1$

	/**
	 * JSON representation key for a file's archive attribute. The value's data
	 * type is a Boolean.
	 */
	public static final String KEY_ATTRIBUTE_ARCHIVE = "Archive"; //$NON-NLS-1$

	/**
	 * JSON representation key for a file's hidden attribute. The value's data
	 * type is a Boolean.
	 */
	public static final String KEY_ATTRIBUTE_HIDDEN = "Hidden"; //$NON-NLS-1$

	/**
	 * JSON representation key for a file's symbolic link attribute. The value's data
	 * type is a Boolean.
	 */
	public static final String KEY_ATTRIBUTE_SYMLINK = "SymLink"; //$NON-NLS-1$

	/**
	 * ETag is an opaque identifier assigned to a specific version of a resource found at a URI. 
	 * If the resource content at that URL ever changes, a new and different ETag is assigned. 
	 */
	public static final String KEY_ETAG = "ETag"; //$NON-NLS-1$

	/**
	 * JSON representation key for a server host attribute. The value's data
	 * type is a String.
	 */
	public static final String KEY_HOST = "Host"; //$NON-NLS-1$

	/**
	 * JSON representation key for a passphrase attribute. The value's data
	 * type is a String.
	 */
	public static final String KEY_PASSPHRASE = "Passphrase"; //$NON-NLS-1$

	/**
	 * JSON representation key for a server port attribute. The value's data
	 * type is an Integer.
	 */
	public static final String KEY_PORT = "Port"; //$NON-NLS-1$

	/**
	 * JSON representation key for a server path attribute. The value's data
	 * type is a String.
	 */
	public static final String KEY_PATH = "Path"; //$NON-NLS-1$

	/**
	 * JSON representation key for indicator that request is handled by long-polling
	 */
	public static final String KEY_LONGPOLLING = "Longpolling"; //$NON-NLS-1$

	/**
	 * JSON representation key for long-polling id
	 */
	public static final String KEY_LONGPOLLING_ID = "LongpollingId"; //$NON-NLS-1$

	/**
	 * JSON representation key for the previous page location of a pageable result.
	 * The value's data type is a String.
	 */
	public static final String KEY_PREVIOUS_LOCATION = "PreviousLocation"; //$NON-NLS-1$

	/**
	 * JSON representation key for the next page location of a pageable result.
	 * The value's data type is a String.
	 */
	public static final String KEY_NEXT_LOCATION = "NextLocation"; //$NON-NLS-1$
}
