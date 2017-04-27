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

package org.eclipse.orion.server.logs;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.logs.objects.ArchivedLogFileResource;
import org.eclipse.orion.server.logs.objects.RollingFileAppenderResource;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;

/**
 * Utility class for common log operations.
 */
public class LogUtils {
	/* 64KB default buffer size */
	private static final int BUFFER_SIZE = Integer.parseInt(PreferenceHelper
			.getString(LogConstants.CONFIG_FILE_LOG_BUFFER_SIZE,
					String.valueOf(64 * 1024)));

	/**
	 * Retrieves the appropriate content type header for the given log file.
	 * Logback supports only GZIP and ZIP compression based on the configuration
	 * file pattern extension. If another or none used, the default plain text
	 * content type is used.
	 * 
	 * @param logFile
	 *            Log file, which content type has to be determined.
	 * @return One of {@link LogConstants.CONTENT_TYPE_PLAIN_TEXT},
	 *         {@link LogConstants.CONTENT_TYPE_GZIP}, or
	 *         {@link LogConstants.CONTENT_TYPE_ZIP}
	 */
	public static String getContentType(File logFile) {
		String name = logFile.getName();
		String extension = null;

		int k = name.lastIndexOf('.');
		if (k > 0 && k < name.length() - 1)
			extension = name.substring(k + 1).toLowerCase();

		if (extension == null)
			return LogConstants.CONTENT_TYPE_PLAIN_TEXT;

		/* logback GZIP compression */
		if ("gz".equals(extension)) //$NON-NLS-1$
			return LogConstants.CONTENT_TYPE_GZIP;

		/* logback ZIP compression */
		if ("zip".equals(extension)) //$NON-NLS-1$
			return LogConstants.CONTENT_TYPE_ZIP;

		/* no compression used */
		return LogConstants.CONTENT_TYPE_PLAIN_TEXT;
	}

	/**
	 * Provides the required log file as a raw file.
	 * 
	 * @param logFile
	 *            Log file to be provided.
	 * @param response
	 *            Client response used to send the file.
	 */
	public static void provideLogFile(File logFile, HttpServletResponse response)
			throws Exception {
		DataInputStream in = null;
		ServletOutputStream output = null;

		try {
			byte[] byteBuffer = new byte[BUFFER_SIZE];
			in = new DataInputStream(new FileInputStream(logFile));
			output = response.getOutputStream();

			response.setHeader("Cache-Control", "no-cache"); //$NON-NLS-1$
			response.setContentType(LogUtils.getContentType(logFile));
			response.setContentLength((int) logFile.length());
			response.setHeader(
					"Content-Disposition", "attachment; filename=\"" + logFile.getName() + "\""); //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$

			int length = 0;
			while (in != null && (length = in.read(byteBuffer)) != -1)
				output.write(byteBuffer, 0, length);

		} finally {
			if (in != null)
				in.close();

			if (output != null)
				output.close();
		}
	}

	/**
	 * Attaches archived log files to a given rolling file appender.
	 * 
	 * @param appender
	 *            Logback rollingFileAppender
	 * @param rollingFileAppenderResource
	 *            Resource which should have attached archived log files.
	 * @param logService
	 *            Log service which should be use to retrieve archived log
	 *            files.
	 */
	public static void attachArchivedLogFiles(
			RollingFileAppender<ILoggingEvent> appender,
			RollingFileAppenderResource rollingFileAppenderResource,
			ILogService logService) {

		File[] files = logService.getArchivedLogFiles(appender);
		if (files == null)
			return;

		List<ArchivedLogFileResource> logFiles = new ArrayList<ArchivedLogFileResource>(
				files.length);

		for (File file : files) {
			ArchivedLogFileResource resource = new ArchivedLogFileResource(
					rollingFileAppenderResource, file);
			logFiles.add(resource);
		}

		rollingFileAppenderResource.setArchivedLogFiles(logFiles);
	}
}
