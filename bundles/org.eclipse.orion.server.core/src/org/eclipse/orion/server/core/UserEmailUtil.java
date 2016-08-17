/*******************************************************************************
 * Copyright (c) 2012, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.core.Activator;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants;

/**
 * Handles sending emails to users
 *
 */
public class UserEmailUtil {

	private static UserEmailUtil util = null;
	/**
	 * The name of the servlet handling email configuration.
	 */
	private static final String PATH_EMAIL_CONFIRMATION = "useremailconfirmation"; //$NON-NLS-1$
	private static final String CONTENTTYPE_HTML_UTF8 = "text/html; charset=UTF-8"; //$NON-NLS-1$

	private static final String EMAIL_CONFIRMATION_FILE = "/emails/EmailConfirmation.txt"; //$NON-NLS-1$
	private static final String EMAIL_CONFIRMATION_RESET_PASS_FILE = "/emails/EmailConfirmationPasswordReset.txt"; //$NON-NLS-1$
	private static final String EMAIL_INACTIVEWORKSPACE_NOTIFICATION_FILE = "/emails/InactiveWorkspaceNotification.txt"; //$NON-NLS-1$
	private static final String EMAIL_INACTIVEWORKSPACE_FINALWARNING_FILE = "/emails/InactiveWorkspaceFinalWarning.txt"; //$NON-NLS-1$
	private static final String EMAIL_PASSWORD_RESET = "/emails/PasswordReset.txt"; //$NON-NLS-1$
	private static final String EMAIL_LAST_DATE_LINK = "<LASTDATE>"; //$NON-NLS-1$
	private static final String EMAIL_DELETION_DATE_LINK = "<DELETIONDATE>"; //$NON-NLS-1$
	private static final String EMAIL_URL_LINK = "<URL>"; //$NON-NLS-1$
	private static final String EMAIL_USER_LINK = "<USER>"; //$NON-NLS-1$
	private static final String EMAIL_PASSWORD_LINK = "<PASSWORD>"; //$NON-NLS-1$
	private static final String EMAIL_ADDRESS_LINK = "<EMAIL>"; //$NON-NLS-1$

	private static final String REMINDER = "** Reminder: "; //$NON-NLS-1$
	private static final Pattern FromPattern = Pattern.compile("^([^<]*)<([^>]*)>\\s*$"); //$NON-NLS-1$

	private String customInactiveWorkspaceFinalWarningContent;
	private String customInactiveWorkspaceNotificationContent;

	private Properties properties;
	private EmailContent confirmationEmail;
	private EmailContent confirmationResetPassEmail;
	private EmailContent inactiveWorkspaceNotificationEmail;
	private EmailContent inactiveWorkspaceFinalWarningEmail;
	private EmailContent passwordResetEmail;

	private class EmailContent {
		private String title;
		private String content;

		public String getTitle() {
			return title;
		}

		public String getContent() {
			return content;
		}

		public EmailContent() {
			super();
		}

		public EmailContent(String fileName) throws URISyntaxException, IOException {
			URL entry = Activator.getDefault().getContext().getBundle().getEntry(fileName);
			if (entry == null) {
				throw new IOException("File not found: " + fileName);
			}
			init(new BufferedReader(new InputStreamReader(entry.openStream())));
		}

		public EmailContent init(BufferedReader reader) throws IOException {
			String line = null;
			try {
				title = reader.readLine();
				StringBuilder stringBuilder = new StringBuilder();
				String ls = System.getProperty("line.separator");
				while ((line = reader.readLine()) != null) {
					stringBuilder.append(line);
					stringBuilder.append(ls);
				}
				content = stringBuilder.toString();
			} finally {
				reader.close();
			}
			return this;
		}
	};

	public UserEmailUtil() {
		properties = System.getProperties();
		properties.put("mail.smtp.starttls.enable", PreferenceHelper.getString(ServerConstants.CONFIG_MAIL_SMTP_STARTTLS, "true"));

		if (PreferenceHelper.getString(ServerConstants.CONFIG_MAIL_SMTP_HOST, null) != null)
			properties.put("mail.smtp.host", PreferenceHelper.getString(ServerConstants.CONFIG_MAIL_SMTP_HOST, null));

		if (PreferenceHelper.getString("mail.smtp.port", null) != null)
			properties.put("mail.smtp.port", PreferenceHelper.getString("mail.smtp.port", null));

		if (PreferenceHelper.getString("mail.smtp.user", null) != null)
			properties.put("mail.smtp.user", PreferenceHelper.getString("mail.smtp.user", null));

		if (PreferenceHelper.getString("mail.smtp.password", null) != null)
			properties.put("mail.smtp.password", PreferenceHelper.getString("mail.smtp.password", null));

		properties.put("mail.smtp.auth", PreferenceHelper.getString("mail.smtp.auth", "false"));

		properties.put("mail.debug", PreferenceHelper.getString("mail.debug", "false"));
	}

	public static UserEmailUtil getUtil() {
		if (util == null) {
			util = new UserEmailUtil();
		}
		return util;
	}

	public boolean isEmailConfigured() {
		return PreferenceHelper.getString("mail.smtp.host", null) != null;
	}

	public void sendEmail(String subject, String messageText, String emailAddress) throws URISyntaxException, IOException, CoreException {
		sendEmail(subject, messageText, emailAddress, false);
	}

	public void sendEmail(String subject, String messageText, String emailAddress, boolean isMultipart) throws URISyntaxException, IOException, CoreException {
		Session session = Session.getInstance(properties, null);
		InternetAddress from;
		try {
			String fromPreference = PreferenceHelper.getString("mail.from", "OrionAdmin");
			Matcher matcher = FromPattern.matcher(fromPreference);
			if (matcher.find()) {
				from = new InternetAddress(matcher.group(2).trim(), matcher.group(1).trim());
			} else {
				from = new InternetAddress(fromPreference);
			}

			InternetAddress to = new InternetAddress(emailAddress);

			MimeMessage message = new MimeMessage(session);
			message.setFrom(from);
			message.addRecipient(Message.RecipientType.TO, to);
			message.setSubject(subject);

			if (isMultipart) {
				MimeBodyPart mbp = new MimeBodyPart();
				mbp.setContent(messageText, CONTENTTYPE_HTML_UTF8);
				MimeMultipart mmp = new MimeMultipart();
				mmp.addBodyPart(mbp);
				message.setContent(mmp);
			} else {
				message.setText(messageText);
			}

			Transport transport = session.getTransport("smtp");
			transport.connect(
				properties.getProperty("mail.smtp.host", null),
				properties.getProperty("mail.smtp.user", null),
				properties.getProperty("mail.smtp.password", null));
			transport.sendMessage(message, message.getAllRecipients());
			transport.close();
		} catch (AddressException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_CORE, e.getMessage(), e));
		} catch (MessagingException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_CORE, e.getMessage(), e));
		}
	}

	public void sendEmailConfirmation(HttpServletRequest req, UserInfo userInfo) throws URISyntaxException, IOException, CoreException {
		URL confirmLocation = new URL(req.getScheme(), req.getServerName(), req.getServerPort(), "/" + PATH_EMAIL_CONFIRMATION);
		if (confirmationEmail == null) {
			confirmationEmail = new EmailContent(EMAIL_CONFIRMATION_FILE);
		}
		String confirmURL = confirmLocation.toString();
		confirmURL += "/" + userInfo.getUniqueId();
		confirmURL += "?" + UserConstants.EMAIL_CONFIRMATION_ID + "=" + userInfo.getProperty(UserConstants.EMAIL_CONFIRMATION_ID);
		sendEmail(
				confirmationEmail.getTitle(),
				confirmationEmail.getContent().replaceAll(EMAIL_USER_LINK, userInfo.getUniqueId()).replaceAll(EMAIL_URL_LINK, confirmURL)
						.replaceAll(EMAIL_ADDRESS_LINK, userInfo.getProperty(UserConstants.EMAIL)), userInfo.getProperty(UserConstants.EMAIL));
	}

	public void sendInactiveWorkspaceNotification(UserInfo userInfo, String lastDate, String deletionDate, String installUrl, boolean isReminder, String emailAddress) throws URISyntaxException, IOException, CoreException {
		if (inactiveWorkspaceNotificationEmail == null) {
			if (customInactiveWorkspaceNotificationContent == null) {
				inactiveWorkspaceNotificationEmail = new EmailContent(EMAIL_INACTIVEWORKSPACE_NOTIFICATION_FILE);
			} else {
				inactiveWorkspaceNotificationEmail = new EmailContent().init(new BufferedReader(new StringReader(customInactiveWorkspaceNotificationContent)));
			}
		}
		sendEmail((isReminder ? REMINDER : "") + inactiveWorkspaceNotificationEmail.getTitle(),
				inactiveWorkspaceNotificationEmail.getContent().replaceAll(EMAIL_LAST_DATE_LINK, lastDate).replaceAll(EMAIL_DELETION_DATE_LINK, deletionDate).replaceAll(EMAIL_URL_LINK, installUrl),
				emailAddress != null ? emailAddress : userInfo.getProperty(UserConstants.EMAIL),
				true);
	}

	public void sendInactiveWorkspaceFinalWarning(UserInfo userInfo, String deletionDate, String installUrl, String emailAddress) throws URISyntaxException, IOException, CoreException {
		if (inactiveWorkspaceFinalWarningEmail == null) {
			if (customInactiveWorkspaceFinalWarningContent == null) {
				inactiveWorkspaceFinalWarningEmail = new EmailContent(EMAIL_INACTIVEWORKSPACE_FINALWARNING_FILE);
			} else {
				inactiveWorkspaceFinalWarningEmail = new EmailContent().init(new BufferedReader(new StringReader(customInactiveWorkspaceFinalWarningContent)));
			}
		}
		sendEmail(inactiveWorkspaceFinalWarningEmail.getTitle(),
				inactiveWorkspaceFinalWarningEmail.getContent().replaceAll(EMAIL_DELETION_DATE_LINK, deletionDate).replaceAll(EMAIL_URL_LINK, installUrl),
				emailAddress != null ? emailAddress : userInfo.getProperty(UserConstants.EMAIL),
				true);
	}

	public void sendResetPasswordConfirmation(URI baseURI, UserInfo userInfo) throws URISyntaxException, IOException, CoreException {
		if (confirmationResetPassEmail == null) {
			confirmationResetPassEmail = new EmailContent(EMAIL_CONFIRMATION_RESET_PASS_FILE);
		}
		String confirmURL = baseURI.toURL().toString();
		confirmURL += "/" + userInfo.getUniqueId();
		confirmURL += "?" + UserConstants.PASSWORD_RESET_ID + "=" + userInfo.getProperty(UserConstants.PASSWORD_RESET_ID);
		sendEmail(confirmationResetPassEmail.getTitle(),
				confirmationResetPassEmail.getContent().replaceAll(EMAIL_URL_LINK, confirmURL).replaceAll(EMAIL_USER_LINK, userInfo.getUniqueId()),
				userInfo.getProperty(UserConstants.EMAIL));
	}

	public void sendPasswordResetEmail(UserInfo userInfo) throws URISyntaxException, IOException, CoreException {
		if (passwordResetEmail == null) {
			passwordResetEmail = new EmailContent(EMAIL_PASSWORD_RESET);
		}
		sendEmail(
				passwordResetEmail.getTitle(),
				passwordResetEmail.getContent().replaceAll(EMAIL_USER_LINK, userInfo.getUniqueId())
						.replaceAll(EMAIL_PASSWORD_LINK, userInfo.getProperty(UserConstants.PASSWORD)), userInfo.getProperty(UserConstants.EMAIL));
	}

	public void setInactivateWorkspaceFinalWarningContent(String value) {
		customInactiveWorkspaceFinalWarningContent = value;
	}

	public void setInactivateWorkspaceNotificationContent(String value) {
		customInactiveWorkspaceNotificationContent = value;
	}
}
