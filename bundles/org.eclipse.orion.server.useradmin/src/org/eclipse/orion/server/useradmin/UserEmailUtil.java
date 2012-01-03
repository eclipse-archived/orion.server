/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;

public class UserEmailUtil {

	private static UserEmailUtil util = null;
	private static final String EMAIL_CONFIRMATION_FILE = "emails/EmailConfirmation.txt";
	private static final String EMAIL_URL_LINK = "<EMAIL_URL>";
	private Properties properties;
	private String confirmationEmail;
	private String confirmationEmailTitle; //first line of the file

	public UserEmailUtil() {
		properties = System.getProperties();
		properties.put("mail.smtp.starttls.enable", "true");

		if (PreferenceHelper.getString(ServerConstants.CONFIG_MAIL_SMTP_HOST, null) != null)
			properties.put("mail.smtp.host", PreferenceHelper.getString(ServerConstants.CONFIG_MAIL_SMTP_HOST, null));

		if (PreferenceHelper.getString("mail.smtp.port", null) != null)
			properties.put("mail.smtp.port", PreferenceHelper.getString("mail.smtp.port", null));

		if (PreferenceHelper.getString("mail.smtp.user", null) != null)
			properties.put("mail.smtp.user", PreferenceHelper.getString("mail.smtp.user", null));

		if (PreferenceHelper.getString("mail.smtp.password", null) != null)
			properties.put("mail.smtp.password", PreferenceHelper.getString("mail.smtp.password", null));

		properties.put("mail.smtp.auth", PreferenceHelper.getString("mail.smtp.auth", "false"));
	}

	public static UserEmailUtil getUtil() {
		if (util == null) {
			util = new UserEmailUtil();
		}
		return util;
	}

	private void initConfirmationEmail() throws URISyntaxException, IOException {
		File emailFile = new File(FileLocator.resolve(UserAdminActivator.getDefault().getBundleContext().getBundle().getEntry(EMAIL_CONFIRMATION_FILE)).toURI());
		BufferedReader reader = new BufferedReader(new FileReader(emailFile));
		String line = null;
		try {
			confirmationEmailTitle = reader.readLine();
			StringBuilder stringBuilder = new StringBuilder();
			String ls = System.getProperty("line.separator");
			while ((line = reader.readLine()) != null) {
				stringBuilder.append(line);
				stringBuilder.append(ls);
			}
			confirmationEmail = stringBuilder.toString();
		} finally {
			reader.close();
		}
	}

	private void sendEmail(String subject, String messageText, String emailAddress) throws URISyntaxException, IOException, MessagingException {
		Session session = Session.getInstance(properties, null);
		InternetAddress from = new InternetAddress(PreferenceHelper.getString("mail.from", "Orion Admin"));
		InternetAddress to = new InternetAddress(emailAddress);

		MimeMessage message = new MimeMessage(session);
		message.setFrom(from);
		message.addRecipient(Message.RecipientType.TO, to);

		message.setSubject(subject);
		message.setText(messageText);

		Transport transport = session.getTransport("smtp");
		transport.connect(properties.getProperty("mail.smtp.host", null), properties.getProperty("mail.smtp.user", null), properties.getProperty("mail.smtp.password", null));
		transport.sendMessage(message, message.getAllRecipients());
		transport.close();
	}

	public void sendEmailConfirmation(URI baseURI, User user) throws URISyntaxException, IOException, MessagingException {
		if (confirmationEmail == null) {
			initConfirmationEmail();
		}
		String confirmURL = baseURI.toURL().toString();
		confirmURL += "/" + user.getUid();
		confirmURL += "?" + UserConstants.KEY_CONFIRMATION_ID + "=" + user.getConfirmationId();
		sendEmail(confirmationEmailTitle, confirmationEmail.replace(EMAIL_URL_LINK, confirmURL), user.getEmail());
	}

}
