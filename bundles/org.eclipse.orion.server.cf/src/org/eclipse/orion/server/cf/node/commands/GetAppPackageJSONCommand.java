package org.eclipse.orion.server.cf.node.commands;

import java.io.InputStreamReader;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.cf.node.CFNodeJSConstants;
import org.eclipse.orion.server.cf.node.objects.PackageJSON;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetAppPackageJSONCommand extends AbstractNodeCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private PackageJSON packageJSON;
	private String commandName;

	protected GetAppPackageJSONCommand(Target target, IFileStore appStore) {
		super(target, appStore);
		this.commandName = NLS.bind("Get package.json for app: {0}", appStore.toString());
	}

	public PackageJSON getPackageJSON() {
		return this.packageJSON;
	}

	@Override
	protected ServerStatus _doIt() {
		IFileStore packageJSONStore = appStore.getChild(CFNodeJSConstants.PACKAGE_JSON_FILE_NAME);
		IFileInfo packageJSONInfo = packageJSONStore.fetchInfo();
		if (!packageJSONInfo.exists() || packageJSONInfo.isDirectory())
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not find a package.json file in the application.", null);

		InputStreamReader reader = null;
		try {
			reader = new InputStreamReader(packageJSONStore.openInputStream(EFS.NONE, null), "UTF-8");
			packageJSON = new PackageJSON(new JSONObject(new JSONTokener(reader)));
			return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null, null);
		} catch (JSONException e) {
			// Invalid JSON, or root is not a JSONObject
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "The application's package.json file is not valid.", null);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured while performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		} finally {
			IOUtilities.safeClose(reader);
		}
	}
}