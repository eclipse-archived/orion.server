package org.eclipse.orion.server.cf.node.commands;

import java.io.*;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.UUID;
import java.util.zip.*;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.server.cf.commands.UploadBitsCommand;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.orion.server.cf.node.CFNodeJSConstants;
import org.eclipse.orion.server.cf.node.objects.PackageJSON;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.cf.utils.PackageUtils;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InjectDebugAndUploadBitsCommand extends UploadBitsCommand {
	private static final String GENERIC_ERROR = "An exception occurred while performing operation {0}";
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App app;
	private String password;
	private String urlPrefix;

	private File modifiedAppPackage;

	public InjectDebugAndUploadBitsCommand(Target target, App app, String password, String urlPrefix) {
		super(target, app);
		this.commandName = NLS.bind("Add debug dependencies and upload application {0} bits (guid: {1})", new String[] {app.getName(), app.getGuid()});
		this.app = app;
		this.password = password;
		this.urlPrefix = urlPrefix == null ? "" : urlPrefix;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.orion.server.cf.commands.UploadBitsCommand#createAppPackage()
	 */
	protected ServerStatus createAppPackage() throws CoreException, IOException {
		MultiServerStatus status = new MultiServerStatus();

		/* If it doesn't seem to be a node.js app, return an error */
		GetAppPackageJSONCommand getPackageJsonCommand = new GetAppPackageJSONCommand(target, app);
		ServerStatus jobStatus = (ServerStatus) getPackageJsonCommand.doIt(); /* FIXME: unsafe type cast */
		status.add(jobStatus);
		if (!jobStatus.isOK())
			return status;
		PackageJSON packageJSON = getPackageJsonCommand.getPackageJSON();

		/* find the app start command*/
		GetAppStartCommandCommand getStartCommand = new GetAppStartCommandCommand(target, app, packageJSON);
		jobStatus = (ServerStatus) getStartCommand.doIt(); /* FIXME: unsafe type cast */
		status.add(jobStatus);
		if (!jobStatus.isOK())
			return status;

		try {
			/* Inject debug requirements into manifest and package.json */
			String modifiedStartCommand = this.getDebugStartCommand(getStartCommand.getCommand());
			PackageJSON modifiedPackageJSON = getModifiedPackageJSON(packageJSON, modifiedStartCommand);
			// TODO write a modified Procfile as well?
			String modifiedManifest = getModifiedManifest(modifiedStartCommand);

			/* Prepare the modified app package file */
			File file = PackageUtils.getApplicationPackage(app.getAppStore());
			File modifiedFile = getModifiedZip(file, modifiedPackageJSON, modifiedManifest);
			file.delete(); // delete the old one

			modifiedAppPackage = modifiedFile;

			return status;
		} catch (Exception e) {
			String msg = NLS.bind("An exception occurred while performing operation {0}", commandName);
			logger.error(msg, e);
			status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return status;
		}
	}

	@Override
	protected File getCreatedAppPackage() {
		return modifiedAppPackage;
	}

	/**
	 * Returns a copy of the given application package with certain files replaced.
	 * @param file The original application package zip.
	 * @param modifiedPackageJSON The modified package.json to inject.
	 * @param modifiedManifest The modified manifest.yml to inject.
	 * @return The modified application package.
	 */
	private File getModifiedZip(File file, PackageJSON modifiedPackageJSON, String modifiedManifest) throws IOException, FileNotFoundException {
		ZipFile zip = null;
		ZipOutputStream zos = null;
		File newFile = null;
		try {
			zip = new ZipFile(file);
			newFile = File.createTempFile(UUID.randomUUID().toString(), ".zip"); //$NON-NLS-1$
			zos = new ZipOutputStream(new FileOutputStream(newFile));

			// Generate a new zip by iterating the old one and replacing the 2 special files
			for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					zos.putNextEntry(entry);
				} else {
					String name = entry.getName();
					if (CFNodeJSConstants.PACKAGE_JSON_FILE_NAME.equals(name)) {
						// Write the modified package.json
						zos.putNextEntry(new ZipEntry(name));
						zos.write(modifiedPackageJSON.getJSON().toString().getBytes("UTF-8")); //$NON-NLS-1$
					} else if (ManifestConstants.MANIFEST_FILE_NAME.equals(name)) {
						// Write the modified manifest.yml
						zos.putNextEntry(new ZipEntry(name));
						zos.write(modifiedManifest.getBytes("UTF-8")); //$NON-NLS-1$
					} else {
						// Just write the existing entry as-is
						zos.putNextEntry(entry);
						IOUtilities.pipe(zip.getInputStream(entry), zos);
					}
				}
				zos.closeEntry();
			}
			return newFile;
		} catch (UnsupportedEncodingException e) {
			// can't happen
			throw new RuntimeException(e);
		} catch (IOException e) {
			// Clean up
			if (newFile != null)
				newFile.delete();
			throw e;
		} finally {
			IOUtilities.safeClose(zos);
			// ZipFile doesn't implement Closeable
			try {
				zip.close();
			} catch (Exception e) {
			}
		}
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

			// Add or update start script
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

	private String getModifiedManifest(String modifiedStartCommand) throws InvalidAccessException {
		// TODO create setters on ManifestParseTree. Then clone app.getManifest() and call setters instead
		String manifest = app.getManifest().toString();
		return manifest.replaceFirst(" command: .+$", " command: " + modifiedStartCommand);
	}

	private String getDebugStartCommand(String command) {
		return MessageFormat.format(CFNodeJSConstants.CF_LAUNCHER_COMMAND_FORMAT, password, urlPrefix, command);
	}

}
