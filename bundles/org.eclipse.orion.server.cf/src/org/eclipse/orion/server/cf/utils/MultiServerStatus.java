/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.utils;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.ServerStatus;
import org.json.JSONObject;

public class MultiServerStatus extends ServerStatus {
	private List<ServerStatus> statuses;

	public MultiServerStatus() {
		super(Status.OK_STATUS, HttpServletResponse.SC_OK);
		statuses = new ArrayList<ServerStatus>();
	}

	public MultiServerStatus(ServerStatus serverStatus) {
		super(Status.OK_STATUS, HttpServletResponse.SC_OK);
		statuses = new ArrayList<ServerStatus>();
		add(serverStatus);
	}

	public boolean add(MultiServerStatus status) {
		for (ServerStatus s : status.getStatuses())
			if (!add(s))
				return false;

		return true;
	}

	public boolean add(ServerStatus status) {
		return statuses.add(status);
	}

	public ServerStatus getLastStatus() {
		if (statuses.size() < 1)
			return null;

		return statuses.get(statuses.size() - 1);
	}

	public List<String> getMessages() {
		List<String> messages = new ArrayList<String>();
		for (ServerStatus status : statuses)
			messages.add(status.getMessage());

		return messages;
	}

	public List<ServerStatus> getStatuses() {
		return statuses;
	}

	@Override
	public IStatus[] getChildren() {
		return (IStatus[]) statuses.toArray();
	}

	@Override
	public int getCode() {
		if (!statuses.isEmpty())
			return getLastStatus().getCode();
		else
			return super.getCode();
	}

	@Override
	public Throwable getException() {
		if (!statuses.isEmpty())
			return getLastStatus().getException();
		else
			return super.getException();
	}

	@Override
	public String getMessage() {
		if (!statuses.isEmpty())
			return getLastStatus().getMessage();
		else
			return super.getMessage();
	}

	@Override
	public String getPlugin() {
		if (!statuses.isEmpty())
			return getLastStatus().getPlugin();
		else
			return super.getPlugin();
	}

	@Override
	public int getSeverity() {
		if (!statuses.isEmpty())
			return getLastStatus().getSeverity();
		else
			return super.getSeverity();
	}

	@Override
	public boolean isMultiStatus() {
		return false;
	}

	@Override
	public boolean isOK() {
		if (!statuses.isEmpty())
			return getLastStatus().isOK();
		else
			return super.isOK();
	}

	@Override
	public boolean matches(int severityMask) {
		if (!statuses.isEmpty())
			return getLastStatus().matches(severityMask);
		else
			return super.matches(severityMask);
	}

	@Override
	public JSONObject getJsonData() {
		if (!statuses.isEmpty())
			return getLastStatus().getJsonData();
		else
			return super.getJsonData();
	}

	@Override
	public JSONObject toJSON() {
		if (!statuses.isEmpty())
			return getLastStatus().toJSON();
		else
			return super.toJSON();
	}
}
