package org.eclipse.orion.server.cf.node.commands;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.commands.AbstractCFCommand;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.node.CFNodeJSConstants;
import org.eclipse.orion.server.cf.node.objects.PackageJSON;
import org.eclipse.orion.server.cf.node.utils.ProcfileUtils;
import org.eclipse.orion.server.cf.node.utils.ProcfileUtils.IProcfileEntry;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetAppStartCommandCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private App app;
	private String command;
	private PackageJSON packageJSON;

	protected GetAppStartCommandCommand(Target target, App app, PackageJSON packageJSON) {
		super(target);
		this.app = app;
		this.packageJSON = packageJSON;
	}

	@Override
	protected ServerStatus _doIt() {
		/* To determine the start command, use the same algorithm as the CF Buildpack for Node.js.
		 * We check for:
		 * 1) "command" in manifest.yml
		 * 2) "web" process in Procfile
		 * 3) "start" script in package.json
		 * 4) a file named server.js
		 */
		command = getStartCommandFromManifest();
		command = command != null ? command : getStartCommandFromProcfile();
		command = command != null ? command : getStartCommandFromPackageJSON();
		command = command != null ? command : getStartCommandFromServerJS();

		if (command == null)
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not determine application start command.", null);
		return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null, null);
	}

	public String getCommand() {
		return this.command;
	}

	/**
	 * @return The command as given in the manifest, or null.
	 */
	private String getStartCommandFromManifest() {
		try {
			ManifestParseTree manifest = app.getManifest();
			ManifestParseTree applicationNode = manifest.get(CFProtocolConstants.V2_KEY_APPLICATIONS).get(0);
			ManifestParseTree commandNode = applicationNode.getOpt(CFProtocolConstants.V2_KEY_COMMAND);
			return (commandNode != null) ? commandNode.getValue() : null; //$NON-NLS-1$
		} catch (InvalidAccessException e) {
			return null;
		}
	}

	/**
	 * @return The <tt>web</tt> command if a <tt>Procfile</tt> exists and has such a command. Otherwise, null. 
	 */
	private String getStartCommandFromProcfile() {
		IFileStore procfileStore = app.getAppStore().getChild(CFNodeJSConstants.PROCFILE_FILE_NAME);
		IFileInfo procfileInfo = procfileStore.fetchInfo();
		if (!procfileInfo.exists() || procfileInfo.isDirectory())
			return null;

		try {
			IProcfileEntry[] entries = ProcfileUtils.parseProcfile(procfileStore);
			for (IProcfileEntry entry : entries) {
				if (CFNodeJSConstants.PROCESS_TYPE_WEB.equals(entry.getProcessType()))
					return entry.getCommand();
			}
			return null;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return null;
		} catch (CoreException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	/**
	 * @return The scripts::start string, if a <tt>package.json</tt> file exists and has such a string. Otherwise, null.
	 */
	private String getStartCommandFromPackageJSON() {
		if (packageJSON == null)
			return null;
		try {
			JSONObject scripts = packageJSON.getJSON().getJSONObject(CFNodeJSConstants.KEY_NPM_SCRIPTS);
			return scripts.getString(CFNodeJSConstants.KEY_NPM_SCRIPTS_START);
		} catch (JSONException e) {
			// Field not present or not of the expected type
			return null;
		}
	}

	/**
	 * @return <tt>"node server.js"</tt>, if a file exists named <tt>server.js</tt>. Otherwise, null.
	 */
	private String getStartCommandFromServerJS() {
		IFileStore store = app.getAppStore();
		IFileInfo serverJS = store.getChild(CFNodeJSConstants.SERVER_JS_FILE_NAME).fetchInfo();
		if (!serverJS.exists() || serverJS.isDirectory())
			return null;
		return NLS.bind(CFNodeJSConstants.START_SERVER_COMMAND, CFNodeJSConstants.SERVER_JS_FILE_NAME);
	}

}
