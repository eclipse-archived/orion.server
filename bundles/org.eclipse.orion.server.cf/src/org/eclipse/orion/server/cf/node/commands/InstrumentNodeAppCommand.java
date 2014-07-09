package org.eclipse.orion.server.cf.node.commands;

import java.io.IOException;
import java.text.MessageFormat;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.orion.server.cf.node.CFNodeJSConstants;
import org.eclipse.orion.server.cf.node.objects.PackageJSON;
import org.eclipse.orion.server.cf.node.utils.ProcfileUtils;
import org.eclipse.orion.server.cf.node.utils.ProcfileUtils.IProcfileEntry;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instruments a Node.js application for debugging.
 */
public class InstrumentNodeAppCommand extends AbstractNodeCFCommand {
	private static final int PACKAGE_JSON_INDENT = 2;

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private ManifestParseTree manifest;
	private String commandName;
	private String password;
	private String urlPrefix;

	public InstrumentNodeAppCommand(Target target, IFileStore appStore, ManifestParseTree manifest, String password, String urlPrefix) {
		super(target, appStore);
		this.manifest = manifest;
		this.commandName = NLS.bind("Instrument app {0} for debug", new String[] {appStore.toString()});
		this.password = password;
		this.urlPrefix = urlPrefix == null ? "" : urlPrefix;
	}

	@Override
	protected ServerStatus _doIt() {
		if (password == null)
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Password must be provided", null);
		if (urlPrefix == null)
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "UrlPrefix must be provided", null);

		MultiServerStatus status = new MultiServerStatus();

		// Get the package.json file
		GetAppPackageJSONCommand getPackageJsonCommand = new GetAppPackageJSONCommand(target, appStore);
		ServerStatus jobStatus = (ServerStatus) getPackageJsonCommand.doIt(); /* FIXME: unsafe type cast */
		status.add(jobStatus);
		if (!jobStatus.isOK())
			return status;
		PackageJSON packageJSON = getPackageJsonCommand.getPackageJSON();

		// Find the app start command
		GetAppStartCommand getStartCommand = new GetAppStartCommand(target, appStore, manifest, packageJSON);
		jobStatus = (ServerStatus) getStartCommand.doIt(); /* FIXME: unsafe type cast */
		status.add(jobStatus);
		if (!jobStatus.isOK())
			return status;

		try {
			String modifiedStartCommand = this.getDebugStartCommand(getStartCommand.getCommand());

			// Write the modified files to the app folder
			// manifest.yml
			IOUtilities.pipe(IOUtilities.toInputStream(getModifiedManifest()), appStore.getChild(ManifestConstants.MANIFEST_FILE_NAME).openOutputStream(EFS.NONE, null));

			// package.json
			PackageJSON modifiedPackageJSON = getModifiedPackageJSON(packageJSON, modifiedStartCommand);
			String prettyPackageJSON = modifiedPackageJSON.getJSON().toString(PACKAGE_JSON_INDENT);
			IOUtilities.pipe(IOUtilities.toInputStream(prettyPackageJSON), appStore.getChild(CFNodeJSConstants.PACKAGE_JSON_FILE_NAME).openOutputStream(EFS.NONE, null));

			// Procfile
			IOUtilities.pipe(IOUtilities.toInputStream(getModifiedProcfile()), appStore.getChild(CFNodeJSConstants.PROCFILE_FILE_NAME).openOutputStream(EFS.NONE, null));

			return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null /*empty response*/, null);
		} catch (IOException e) {
			// problem writing one or more of the modified files
			String msg = NLS.bind("An exception occurred while instrumenting the application: {0}", e.getMessage());
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		} catch (Exception e) {
			String msg = NLS.bind("An exception occurred while performing operation {0}", commandName);
			logger.error(msg, e);
			status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return status;
		}
	}

	private String getDebugStartCommand(String originalCommand) {
		if ("".equals(urlPrefix)) { //$NON-NLS-1$
			return MessageFormat.format("node_modules/.bin/launcher --password \"{0}\" -- {1}", password, originalCommand); //$NON-NLS-1$
		}
		return MessageFormat.format("node_modules/.bin/launcher --password \"{0}\" --urlprefix \"{1}\" -- {2}", password, urlPrefix, originalCommand); //$NON-NLS-1$
	}

	private PackageJSON getModifiedPackageJSON(PackageJSON originalPackage, String modifiedStartCommand) {
		JSONObject original = originalPackage.getJSON();
		try {
			JSONObject modified = new JSONObject(original, JSONObject.getNames(original));

			// Add dependency
			JSONObject deps = modified.optJSONObject(CFNodeJSConstants.KEY_NPM_DEPENDENCIES);
			if (deps == null)
				deps = new JSONObject();
			deps.put(CFNodeJSConstants.KEY_CF_LAUNCHER_PACKAGE, CFNodeJSConstants.VALUE_CF_LAUNCHER_VERSION);
			modified.put(CFNodeJSConstants.KEY_NPM_DEPENDENCIES, deps);

			// Add or update npm start script
			JSONObject scripts = modified.optJSONObject(CFNodeJSConstants.KEY_NPM_SCRIPTS);
			if (scripts == null)
				scripts = new JSONObject();
			scripts.put(CFNodeJSConstants.KEY_NPM_SCRIPTS_START, modifiedStartCommand);
			modified.put(CFNodeJSConstants.KEY_NPM_SCRIPTS, scripts);
			return new PackageJSON(modified);
		} catch (JSONException e) {
			// Cannot happen
			return null;
		}
	}

	private String getModifiedManifest() {
		// Just reference the npm start script
		return manifest.toString().replaceFirst(" command: .+$", " command: npm start"); //$NON-NLS-1$  //$NON-NLS-2$
	}

	private String getModifiedProcfile() throws IOException, CoreException {
		// Reference the npm start script
		String newWebProcessType = "web: npm start"; //$NON-NLS-1$
		IProcfileEntry[] entries = ProcfileUtils.readProcfileFromFolder(appStore);
		if (entries == null)
			return newWebProcessType;

		StringBuilder buf = new StringBuilder();
		for (IProcfileEntry entry : entries) {
			if (CFNodeJSConstants.PROCESS_TYPE_WEB.equals(entry.getProcessType()))
				buf.append(newWebProcessType);
			else
				buf.append(entry.getProcessType()).append(": ").append(entry.getCommand()); //$NON-NLS-1$
			buf.append("\n"); //$NON-NLS-1$
		}
		return buf.toString();
	}
}
