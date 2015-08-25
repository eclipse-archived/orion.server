package org.eclipse.orion.server.authentication.github;

import org.eclipse.orion.server.authentication.oauth.OAuthActivator;
import org.eclipse.orion.server.authentication.oauth.OAuthParamsFactory;
import org.eclipse.orion.server.authentication.oauth.github.GitHubOAuthParamsFactory;

/**
 * @author mwlodarczyk
 *
 */

public class Activator extends OAuthActivator {
	@Override
	protected OAuthParamsFactory getOAuthParamsFactory() {
		return new GitHubOAuthParamsFactory();
	}
}

