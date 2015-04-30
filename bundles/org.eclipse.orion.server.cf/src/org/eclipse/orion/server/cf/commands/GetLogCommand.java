/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.loggregator.LoggregatorListener;
import org.eclipse.orion.server.cf.loggregator.LoggregatorMessage;
import org.eclipse.orion.server.cf.loggregator.LoggregatorRegistry;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.cf.utils.MultipartMessageReader;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetLogCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String appId;
	private String loggingEndpoint;
	private LoggregatorListener listener;
	private long timestamp = -1;

	public GetLogCommand(Target target, String loggingEndpoint, String appId, long timestamp/*, LoggregatorListener listener*/) {
		super(target);
		this.commandName = "Get App Log"; //$NON-NLS-1$
		this.loggingEndpoint = loggingEndpoint;
		this.appId = appId;
		this.listener = LoggregatorRegistry.getDefault().getListener(appId);
		this.timestamp = timestamp;
	}

	public ServerStatus _doIt() {
		try {
			if (this.loggingEndpoint.startsWith("wss://"))
				this.loggingEndpoint = this.loggingEndpoint.replace("wss://", "https://");
			else if (this.loggingEndpoint.startsWith("ws://"))
				this.loggingEndpoint = this.loggingEndpoint.replace("ws://", "http://");

			URI infoURI = URIUtil.toURI(new URL(loggingEndpoint)).resolve("/recent?app=" + this.appId);

			GetMethod getLogMethod = new GetMethod(infoURI.toString());

			getLogMethod.addRequestHeader(new Header("Content-Type", "multipart/form-data"));
			if (target.getCloud().getAccessToken() != null)
				getLogMethod.addRequestHeader(new Header("Authorization", "bearer " + target.getCloud().getAccessToken().getString("access_token")));

			ServerStatus getLogStatus = HttpUtil.executeMethod(getLogMethod);
			if (!getLogStatus.isOK())
				return getLogStatus;

			Header contentType = getLogMethod.getResponseHeader("Content-Type");
			String contentTypeValue = contentType.getValue();
			String[] values = contentTypeValue.split(";");

			String boundary = null;
			for (int i = 0; i < values.length; i++) {
				if (values[i].trim().startsWith("boundary")) {
					boundary = values[i].split("=")[1];
					break;
				}
			}

			if (boundary == null) {
				String msg = NLS.bind("An error occured when performing operation {0}. Boundary in response header not found.", commandName); //$NON-NLS-1$
				logger.error(msg);
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, null);
			}

			InputStream responseStream = getLogMethod.getResponseBodyAsStream();
			try {
				byte[] messageBody = getMessageBody(responseStream);
				MultipartMessageReader multipartReader = new MultipartMessageReader(boundary, messageBody);
				while (multipartReader.readNextPart()) {
					try {
						LoggregatorMessage.Message message = LoggregatorMessage.Message.parseFrom(multipartReader.getPart());
						listener.add(message);
					} catch (Exception ex) {
						logger.error("Problem while reading logs", ex);
					}
				}
			} finally {
				responseStream.close();
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	private byte[] getMessageBody(InputStream responseStream) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int readBytes;
		byte[] data = new byte[1024];
		while ((readBytes = responseStream.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, readBytes);
		}

		byte[] messageBody = buffer.toByteArray();
		return messageBody;
	}
	
	public JSONArray getMessages() {
		return timestamp < listener.getLastTimestamp() ? this.listener.getMessagesJSON() : new JSONArray();
	}
	
	public long getLastTimestamp() {
		return this.listener.getLastTimestamp();
	}
}
