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

package org.eclipse.orion.server.logs.jobs;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskJob;
import org.eclipse.orion.server.logs.ILogService;
import org.eclipse.orion.server.logs.objects.ArchivedLogFileResource;
import org.eclipse.orion.server.logs.objects.FixedWindowRollingPolicyResource;
import org.eclipse.orion.server.logs.objects.RollingFileAppenderResource;
import org.eclipse.orion.server.logs.objects.RollingPolicyResource;
import org.eclipse.orion.server.logs.objects.SizeBasedTriggeringPolicyResource;
import org.eclipse.orion.server.logs.objects.TimeBasedRollingPolicyResource;
import org.eclipse.orion.server.logs.objects.TriggeringPolicyResource;
import org.eclipse.osgi.util.NLS;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.RollingPolicy;
import ch.qos.logback.core.rolling.RollingPolicyBase;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.TriggeringPolicy;

public class RollingFileAppenderJob extends TaskJob {
	private final ILogService logService;
	private final URI baseLocation;
	private final String appenderName;

	public RollingFileAppenderJob(String userRunningTask, ILogService logService, URI baseLocation, String appenderName) {
		super(userRunningTask, false);
		this.logService = logService;
		this.baseLocation = baseLocation;
		this.appenderName = appenderName;
	}

	private void populateRollingPolicy(RollingPolicyResource policyResource, RollingPolicyBase policy) {
		policyResource.setFileNamePattern(policy.getFileNamePattern());
		policyResource.setCompressionMode(policy.getCompressionMode());
	}

	private TimeBasedRollingPolicyResource populateTimeBasedRollingPolicy(TimeBasedRollingPolicy<ILoggingEvent> policy) {
		TimeBasedRollingPolicyResource policyResource = new TimeBasedRollingPolicyResource();

		/* populate base information */
		populateRollingPolicy(policyResource, policy);

		policyResource.setCleanHistoryOnStart(policy.isCleanHistoryOnStart());
		policyResource.setMaxHistory(policy.getMaxHistory());
		return policyResource;
	}

	private FixedWindowRollingPolicyResource populateFixedWindowRollingPolicy(FixedWindowRollingPolicy policy) {
		FixedWindowRollingPolicyResource policyResource = new FixedWindowRollingPolicyResource();

		/* populate base information */
		populateRollingPolicy(policyResource, policy);

		policyResource.setMinIndex(policy.getMinIndex());
		policyResource.setMaxIndex(policy.getMaxIndex());
		return policyResource;
	}

	private SizeBasedTriggeringPolicyResource populateSizeBasedTriggeringPolicy(
			SizeBasedTriggeringPolicy<ILoggingEvent> policy) {

		SizeBasedTriggeringPolicyResource policyResource = new SizeBasedTriggeringPolicyResource();
		policyResource.setMaxFileSize(policy.getMaxFileSize());
		return policyResource;
	}

	private void populateArchivedLogFiles(RollingFileAppender<ILoggingEvent> appender,
			RollingFileAppenderResource rollingFileAppenderResource) {

		File[] files = logService.getArchivedLogFiles(appender);
		if (files == null)
			return;

		List<ArchivedLogFileResource> logFiles = new ArrayList<ArchivedLogFileResource>(files.length);
		for (File file : files) {
			ArchivedLogFileResource resource = new ArchivedLogFileResource();
			resource.setBaseLocation(baseLocation);
			resource.setName(file.getName());
			resource.setRollingFileAppender(rollingFileAppenderResource);
			logFiles.add(resource);
		}

		rollingFileAppenderResource.setArchivedLogFiles(logFiles);
	}

	@Override
	protected IStatus performJob() {
		try {
			RollingFileAppender<ILoggingEvent> appender = logService.getRollingFileAppender(appenderName);

			if (appender == null) {
				String msg = NLS.bind("Appender not found: {0}", appenderName);
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
			}

			RollingFileAppenderResource rollingFileAppender = new RollingFileAppenderResource();
			rollingFileAppender.setBaseLocation(baseLocation);
			rollingFileAppender.setName(appender.getName());

			rollingFileAppender.setAppend(appender.isAppend());
			rollingFileAppender.setPrudent(appender.isPrudent());
			rollingFileAppender.setStarted(appender.isStarted());

			/* rolling policy used in representation */
			RollingPolicyResource rollingPolicyResource = null;
			RollingPolicy rollingPolicy = appender.getRollingPolicy();

			if (rollingPolicy instanceof TimeBasedRollingPolicy<?>) {
				TimeBasedRollingPolicy<ILoggingEvent> policy = (TimeBasedRollingPolicy<ILoggingEvent>) rollingPolicy;
				rollingPolicyResource = populateTimeBasedRollingPolicy(policy);
			}

			if (rollingPolicy instanceof FixedWindowRollingPolicy) {
				FixedWindowRollingPolicy policy = (FixedWindowRollingPolicy) rollingPolicy;
				rollingPolicyResource = populateFixedWindowRollingPolicy(policy);
			}

			/*
			 * rolling policy might be absent due to configuration errors or
			 * unsupported rolling policies applied to the rolling file
			 * appender.
			 */
			if (rollingPolicyResource != null) {
				rollingFileAppender.setRollingPolicy(rollingPolicyResource);
				populateArchivedLogFiles(appender, rollingFileAppender);
			}

			/* triggering policy used in representation */
			TriggeringPolicyResource triggeringPolicyResource = null;
			TriggeringPolicy<ILoggingEvent> triggeringPolicy = appender.getTriggeringPolicy();

			if (triggeringPolicy instanceof SizeBasedTriggeringPolicy) {
				SizeBasedTriggeringPolicy policy = (SizeBasedTriggeringPolicy) triggeringPolicy;
				triggeringPolicyResource = populateSizeBasedTriggeringPolicy(policy);
			}

			/*
			 * triggering policy might be absent due to configuration errors or
			 * unsupported triggering policies applied to the rolling file
			 * appender.
			 */
			if (triggeringPolicyResource != null)
				rollingFileAppender.setTriggeringPolicy(triggeringPolicyResource);

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, rollingFileAppender.toJSON());

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when looking for an appender: {0}", appenderName);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}