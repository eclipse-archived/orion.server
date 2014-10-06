package org.eclipse.orion.server.cf.node.commands;

import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.node.CFNodeJSConstants;
import org.eclipse.orion.server.cf.node.objects.PackageJSON;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.ServerStatus;
import org.json.JSONException;
import org.json.JSONObject;

public class UninstrumentNodeAppCommand extends AbstractNodeCFCommand {

	private ManifestParseTree manifest;

	public UninstrumentNodeAppCommand(Target target, IFileStore appStore, ManifestParseTree manifest) {
		super(target, appStore);
		this.manifest = manifest;
	}

	@Override
	protected ServerStatus _doIt() {
		MultiServerStatus status = new MultiServerStatus();

		try {
			// Get the package.json file
			GetAppPackageJSONCommand getPackageJsonCommand = new GetAppPackageJSONCommand(target, appStore);
			ServerStatus jobStatus = (ServerStatus) getPackageJsonCommand.doIt(); /* FIXME: unsafe type cast */
			status.add(jobStatus);
			if (!jobStatus.isOK())
				return status;
			PackageJSON packageJSON = getPackageJsonCommand.getPackageJSON();

			// Determine the original start command
			String originalCommand = getOriginalStartCommand(packageJSON);

			// Remove dependency from package json

			// Modify or remove Procfile

			return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null, null);
		} catch (Exception e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not determine application start command.", null);
		}
	}

	private String getOriginalStartCommand(PackageJSON packageJSON) throws JSONException {
		JSONObject scripts = packageJSON.getJSON().getJSONObject(CFNodeJSConstants.KEY_NPM_SCRIPTS);
		String start = scripts.getString(CFNodeJSConstants.KEY_NPM_SCRIPTS_START);
		String[] result = start.split("\\s--\\s");
		if (result.length == 2) {
			return result[1];
		}
		throw new RuntimeException("Could not determin original start command");
	}
}
