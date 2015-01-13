/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search.grep;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.internal.server.servlets.Activator;

/**
 * A job that wraps and runs a search task. We currently limit one running search job per user.
 * 
 * @author Anthony Hunter
 */
public class SearchJob extends Job {
	private SearchOptions options;
	private String username;

	public String getUsername() {
		return username;
	}

	public static final Object FAMILY = "org.eclipse.orion.server.search.jobs.SearchJob";

	@Override
	public boolean belongsTo(Object family) {
		return FAMILY.equals(family);
	}

	private List<SearchResult> files;

	public List<SearchResult> getSearchResults() {
		return files;
	}

	public SearchJob(SearchOptions options) {
		super("Orion Search Job " + options.getUsername());
		this.options = options;
		this.username = options.getUsername();
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		IStatus result = null;
		try {
			FileGrepper grepper = new FileGrepper(options);
			files = grepper.search(options);
			result = Status.OK_STATUS;
		} catch (GrepException exception) {
			result = new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, exception.getLocalizedMessage(), exception);
		}
		return result;
	}

	public static boolean isSearchJobRunning(String remoteUser) {
		Job[] jobs = Job.getJobManager().find(FAMILY);
		for (int i = 0; i < jobs.length; i++) {
			SearchJob searchJob = (SearchJob) jobs[i];
			if (searchJob.getUsername().equals(remoteUser)) {
				return true;
			}
		}
		return false;
	}

}
