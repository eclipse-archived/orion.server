package org.eclipse.orion.server.authentication.google;

import org.eclipse.orion.server.authentication.oauth.OAuthActivator;
import org.eclipse.orion.server.authentication.oauth.OAuthParamsFactory;
import org.eclipse.orion.server.authentication.oauth.google.GoogleOAuthParamsFactory;

/**
 * @author mwlodarczyk
 *
 */

public class Activator extends OAuthActivator {
	@Override
	protected OAuthParamsFactory getOAuthParamsFactory() {
		return new GoogleOAuthParamsFactory();
	}
}

