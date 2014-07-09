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
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Determines the start command for a Node.js application.
 * 
 * <p>To determine the start command, we use the same algorithm as the CF Buildpack for Node.js, ie. check for:</p>
 * <ol>
 * <li>"command" in <tt>manifest.yml</tt></li>
 * <li>"web" process in <tt>Procfile</tt></li>
 * <li>"start" script in <tt>package.json</tt></li>
 * <li>a file named <tt>server.js</tt></li>
 * </ol>
 */
public class GetAppStartCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	interface IStrategy {
		/**
		 * @return The start command, or null if this strategy could not determine the command.
		 */
		String run(IFileStore appStore, ManifestParseTree manifest, PackageJSON packageJSON);
	}

	/**
	 * Gets the start command from the manifest.yml.
	 */
	private final IStrategy fromManifest = new IStrategy() {
		@Override
		public String run(IFileStore appStore, ManifestParseTree manifest, PackageJSON packageJSON) {
			try {
				ManifestParseTree applicationNode = manifest.get(CFProtocolConstants.V2_KEY_APPLICATIONS).get(0);
				ManifestParseTree commandNode = applicationNode.getOpt(CFProtocolConstants.V2_KEY_COMMAND);
				return (commandNode != null) ? commandNode.getValue() : null; //$NON-NLS-1$
			} catch (InvalidAccessException e) {
				return null;
			}
		}
	};

	/**
	 * Gets the <tt>web</tt> process type from a Procfile.
	 */
	private final IStrategy fromProcfile = new IStrategy() {
		@Override
		public String run(IFileStore appStore, ManifestParseTree manifest, PackageJSON packageJSON) {
			IFileStore procfileStore = appStore.getChild(CFNodeJSConstants.PROCFILE_FILE_NAME);
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
	};

	/**
	 * Gets the "start" script given in the "scripts" section of the package.json file.
	 */
	private final IStrategy fromNPMStartScript = new IStrategy() {
		@Override
		public String run(IFileStore appStore, ManifestParseTree manifest, PackageJSON packageJSON) {
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
	};

	/**
	 * Gets a command of <tt>"node server.js"</tt>, if a file exists named server.js.
	 */
	private final IStrategy fromServerJSFile = new IStrategy() {
		@Override
		public String run(IFileStore appStore, ManifestParseTree manifest, PackageJSON packageJSON) {
			IFileInfo serverJS = appStore.getChild(CFNodeJSConstants.SERVER_JS_FILE_NAME).fetchInfo();
			if (!serverJS.exists() || serverJS.isDirectory())
				return null;
			return NLS.bind(CFNodeJSConstants.START_SERVER_COMMAND, CFNodeJSConstants.SERVER_JS_FILE_NAME);
		};
	};

	private final IStrategy[] STRATEGIES = new IStrategy[] {fromManifest, fromProcfile, fromNPMStartScript, fromServerJSFile};

	private IFileStore appStore;
	private ManifestParseTree manifest;
	private PackageJSON packageJSON;
	private String command;

	protected GetAppStartCommand(Target target, IFileStore appStore, ManifestParseTree manifest, PackageJSON packageJSON) {
		super(target);
		this.appStore = appStore;
		this.manifest = manifest;
		this.packageJSON = packageJSON;
	}

	@Override
	protected ServerStatus _doIt() {
		for (IStrategy strategy : STRATEGIES) {
			if ((this.command = strategy.run(appStore, manifest, packageJSON)) != null) {
				// Found one
				return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null, null);
			}
		}
		return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not determine application start command.", null);
	}

	public String getCommand() {
		return this.command;
	}

}
