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
package org.eclipse.orion.server.cf;

public class CFProtocolConstants {

	public static final String KEY_GUID = "Guid"; //$NON-NLS-1$

	public static final String KEY_NAME = "Name"; //$NON-NLS-1$

	public static final String KEY_TIMEOUT = "Timeout"; //$NON-NLS-1$

	public static final String KEY_URL = "Url"; //$NON-NLS-1$

	public static final String KEY_MANAGE_URL = "ManageUrl"; //$NON-NLS-1$

	public static final String KEY_USER = "User"; //$NON-NLS-1$

	public static final String KEY_PASSWORD = "Password"; //$NON-NLS-1$

	public static final String KEY_ORGS = "Orgs"; //$NON-NLS-1$

	public static final String KEY_ORG = "Org"; //$NON-NLS-1$

	public static final String KEY_SPACES = "Spaces"; //$NON-NLS-1$

	public static final String KEY_SPACE = "Space"; //$NON-NLS-1$

	public static final String KEY_STATE = "State"; //$NON-NLS-1$

	public static final String KEY_STARTED = "Started"; //$NON-NLS-1$

	public static final String KEY_STOPPED = "Stopped"; //$NON-NLS-1$

	public static final String KEY_TARGET = "Target"; //$NON-NLS-1$

	public static final String KEY_CONTENTS = "Contents"; //$NON-NLS-1$

	public static final String KEY_APPLICATION = "Application"; //$NON-NLS-1$

	public static final String KEY_SIZE = "Size"; //$NON-NLS-1$

	public static final String JSON_CONTENT_TYPE = "application/json"; //$NON-NLS-1$

	public static final String KEY_DIR = "Dir"; //$NON-NLS-1$

	public static final String KEY_APP = "App"; //$NON-NLS-1$

	public static final String KEY_APPS = "Apps"; //$NON-NLS-1$

	public static final String KEY_CONTENT_LOCATION = "ContentLocation"; //$NON-NLS-1$

	public static final String KEY_FORCE = "Force"; //$NON-NLS-1$

	public static final String KEY_HOST = "Host"; //$NON-NLS-1$

	public static final String KEY_DOMAIN_NAME = "DomainName"; //$NON-NLS-1$

	public static final String KEY_INVALIDATE = "Invalidate"; //$NON-NLS-1$

	public static final String KEY_ROUTE = "Route"; //$NON-NLS-1$

	public static final String KEY_ROUTES = "Routes"; //$NON-NLS-1$

	public static final String KEY_ORPHANED = "Orphaned"; //$NON-NLS-1$

	public static final String KEY_DEBUG_PASSWORD = "DebugPassword"; //$NON-NLS-1$

	public static final String KEY_DEBUG_URL_PREFIX = "DebugUrlPrefix"; //$NON-NLS-1$

	// CF REST API protocol constants

	public static final String V2_KEY_METADATA = "metadata"; //$NON-NLS-1$

	public static final String V2_KEY_GUID = "guid"; //$NON-NLS-1$

	public static final String V2_KEY_SPACE_GUID = "space_guid"; //$NON-NLS-1$

	public static final String V2_KEY_NAME = "name"; //$NON-NLS-1$

	public static final String V2_KEY_INSTANCES = "instances"; //$NON-NLS-1$

	public static final String V2_KEY_BUILDPACK = "buildpack"; //$NON-NLS-1$

	public static final String V2_KEY_COMMAND = "command"; //$NON-NLS-1$

	public static final String V2_KEY_MEMORY = "memory"; //$NON-NLS-1$

	public static final String V6_KEY_MEMORY = "mem"; //$NON-NLS-1$

	public static final String V2_KEY_STACK_GUID = "stack_guid"; //$NON-NLS-1$

	public static final String V2_KEY_ENTITY = "entity"; //$NON-NLS-1$

	public static final String V2_KEY_ORGS = "organizations"; //$NON-NLS-1$

	public static final String V2_KEY_DOMAINS_URL = "domains_url"; //$NON-NLS-1$

	public static final String V2_KEY_ROUTES_URL = "routes_url"; //$NON-NLS-1$

	public static final String V2_KEY_TOTAL_RESULTS = "total_results"; //$NON-NLS-1$

	public static final String V2_KEY_RESOURCES = "resources"; //$NON-NLS-1$

	public static final String V2_KEY_HOST = "host"; //$NON-NLS-1$

	public static final String V2_KEY_DOMAIN_GUID = "domain_guid"; //$NON-NLS-1$

	public static final String V2_KEY_APPLICATION = "application"; //$NON-NLS-1$

	public static final String V2_KEY_STATUS = "status"; //$NON-NLS-1$

	public static final String V2_KEY_FINISHED = "finished"; //$NON-NLS-1$

	public static final String V2_KEY_FAILURE = "failure"; //$NON-NLS-1$

	public static final String V2_KEY_FAILED = "failed"; //$NON-NLS-1$

	public static final String V2_KEY_URL = "url"; //$NON-NLS-1$

	public static final String V2_KEY_SERVICES = "services"; //$NON-NLS-1$

	public static final String V2_KEY_TYPE = "type"; //$NON-NLS-1$

	public static final String V2_KEY_PROVIDER = "provider"; //$NON-NLS-1$

	public static final String V2_KEY_PLAN = "plan"; //$NON-NLS-1$

	public static final String V2_KEY_SERVICE_PLAN_GUID = "service_plan_guid"; //$NON-NLS-1$

	public static final String V2_KEY_SERVICE_INSTANCE_GUID = "service_instance_guid"; //$NON-NLS-1$

	public static final String V2_KEY_APP_GUID = "app_guid"; //$NON-NLS-1$

	public static final String V2_KEY_LABEL = "label"; //$NON-NLS-1$

	public static final String V2_KEY_SERVICE_PLANS = "service_plans"; //$NON-NLS-1$

	public static final String V2_KEY_APPLICATIONS = "applications"; //$NON-NLS-1$

	public static final String V2_KEY_DOMAIN = "domain"; //$NON-NLS-1$

	public static final String V2_KEY_PATH = "path"; //$NON-NLS-1$

	public static final String V2_KEY_APPS = "apps"; //$NON-NLS-1$

	public static final String V2_KEY_ERROR_CODE = "error_code"; //$NON-NLS-1$

	public static final String V2_KEY_ERROR_DESCRIPTION = "description"; //$NON-NLS-1$

	public static final String V2_KEY_ENV = "env"; //$NON-NLS-1$

	public static final String V2_KEY_ENVIRONMENT_JSON = "environment_json"; //$NON-NLS-1$

	public static final String V2_KEY_NO_ROUTE = "no-route"; //$NON-NLS-1$

	public static final String V2_KEY_TIMEOUT = "timeout"; //$NON-NLS-1$
}
