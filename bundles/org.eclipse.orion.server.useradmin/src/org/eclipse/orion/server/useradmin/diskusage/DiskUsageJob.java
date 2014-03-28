package org.eclipse.orion.server.useradmin.diskusage;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.user.profile.IOrionUserProfileConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.user.profile.IOrionUserProfileService;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiskUsageJob extends Job {
	private Logger logger;
	public static final String DISK_USAGE = "diskUsage";
	public static final String DISK_USAGE_FOLDER = "org.eclipse.orion.server.useradmin";

	public DiskUsageJob() {
		super("Orion Disk Usage Data");
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		logger = LoggerFactory.getLogger("org.eclipse.orion.server.account"); //$NON-NLS-1$
		if (!initializeDiskUsageData()) {
			return Status.CANCEL_STATUS;
		}
		if (!updateDiskUsageData()) {
			if (logger.isInfoEnabled()) {
				logger.info("Orion Disk Usage waiting for user metadata service");
			}
			schedule(5000);
			return Status.OK_STATUS;
		}
		// run the disk usage job again in twelve hours (twice a day).
		schedule(43200000);
		return Status.OK_STATUS;
	}

	private boolean updateDiskUsageData() {
		try {
			JSONArray jsonArray = new JSONArray();

			int users = 0;
			long diskUsageTotal = 0;
			UserServiceHelper userServiceHelper = UserServiceHelper.getDefault();
			if (userServiceHelper == null) {
				//bundle providing metastore might not have started yet
				return false;
			}
			IOrionUserProfileService userProfileService = userServiceHelper.getUserProfileService();
			IMetaStore metaStore = OrionConfiguration.getMetaStore();
			String[] userids = userProfileService.getUserNames();
			for (String userId : userids) {
				users++;
				UserInfo userInfo = metaStore.readUser(userId);
				List<String> workspaceIds = userInfo.getWorkspaceIds();
				int projects = 0;
				for (String workspaceId : workspaceIds) {
					WorkspaceInfo workspaceInfo = metaStore.readWorkspace(workspaceId);
					List<String> projectNames = workspaceInfo.getProjectNames();
					projects += projectNames.size();
				}
				File userRoot = OrionConfiguration.getUserHome(userId).toLocalFile(EFS.NONE, null);
				long diskUsage = getFolderSize(userRoot);
				diskUsageTotal += diskUsage;
				IOrionUserProfileNode generalUserProfile = userProfileService.getUserProfileNode(userId, IOrionUserProfileConstants.GENERAL_PROFILE_PART);
				long lastLogin = 0L;
				if (generalUserProfile.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, null) != null) {
					lastLogin = Long.parseLong(generalUserProfile.get(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, "0L"));
				}
				JSONObject userJsonObject = new JSONObject();
				userJsonObject.put("userId", userId);
				userJsonObject.put("name", userInfo.getFullName());
				userJsonObject.put("projects", projects);
				userJsonObject.put("diskUsage", Long.toString(diskUsage));
				userJsonObject.put(IOrionUserProfileConstants.LAST_LOGIN_TIMESTAMP, lastLogin);
				jsonArray.put(userJsonObject);
			}
			JSONObject topJsonObject = new JSONObject();
			topJsonObject.put("users", jsonArray);
			topJsonObject.put("userCount", users);
			topJsonObject.put("diskUsage", Long.toString(diskUsageTotal));
			File diskUsageDataFolder = getDiskUsageDataFolder();
			SimpleMetaStoreUtil.updateMetaFile(diskUsageDataFolder, DISK_USAGE, topJsonObject);
			if (logger.isInfoEnabled()) {
				logger.info("Orion disk usage data initialized"); //$NON-NLS-1$
			}

		} catch (JSONException e) {
			logger.error("Cannot initialize disk usage data file: JSON Error", e); //$NON-NLS-1$
			return false;
		} catch (CoreException e) {
			logger.error("Cannot initialize disk usage data file", e); //$NON-NLS-1$
			return false;
		}
		return true;
	}

	private long getFolderSize(File folder) {
		StringBuffer commandOutput = new StringBuffer();
		Process process;
		try {
			process = Runtime.getRuntime().exec("du -bs " + folder.toString());
			process.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line = "";
			while ((line = reader.readLine()) != null) {
				commandOutput.append(line + "\n");
			}

		} catch (Exception e) {
			return 0L;
		}

		String size = commandOutput.toString();
		if (size.indexOf("\t") == -1) {
			return 0L;
		}
		return Long.parseLong(size.substring(0, size.indexOf("\t")));
	}

	/**
	 * Initialize the disk usage data if it does not exist with a simple JSON file.
	 *   
	 * @return true if a previous version of the disk usage data exists or a new simple data file was created. 
	 */
	private boolean initializeDiskUsageData() {
		String metastore = OrionConfiguration.getMetaStorePreference();

		if (!ServerConstants.CONFIG_META_STORE_SIMPLE.equals(metastore)) {
			// Disk usage data only supported by the simple metadata storage
			return false;
		}

		File diskUsageDataFolder = getDiskUsageDataFolder();
		if (diskUsageDataFolder != null) {
			if (SimpleMetaStoreUtil.isMetaFile(diskUsageDataFolder, DISK_USAGE)) {
				// the disk usage data exists from a previous run
				return true;
			}
		}

		try {
			// since the disk usage data does not exist, create one with an empty data
			JSONObject jsonObject = new JSONObject();
			jsonObject.put(DISK_USAGE, "disk usage job has not been run yet: no current data");

			SimpleMetaStoreUtil.createMetaFile(diskUsageDataFolder, DISK_USAGE, jsonObject);
			if (logger.isInfoEnabled()) {
				logger.info("Orion disk usage data initialized"); //$NON-NLS-1$
			}

		} catch (JSONException e) {
			logger.error("Cannot initialize disk usage data file: JSON Error", e); //$NON-NLS-1$
			return false;
		}

		return true;
	}

	/**
	 * Verify that the disk usage data folder exists.
	 * 
	 * @return true if the disk usage data folder exists.
	 */
	private File getDiskUsageDataFolder() {
		try {
			File root = OrionConfiguration.getUserHome(null).toLocalFile(EFS.NONE, null);
			File parentFolder = new File(root, "/.metadata/.plugins/");
			if (!parentFolder.exists() || !parentFolder.isDirectory()) {
				logger.error("Orion disk usage data: cannot open folder " + parentFolder.toString()); //$NON-NLS-1$
				return null;
			}
			if (!SimpleMetaStoreUtil.isMetaFolder(parentFolder, DISK_USAGE_FOLDER)) {
				SimpleMetaStoreUtil.createMetaFolder(parentFolder, DISK_USAGE_FOLDER);
			}
			return SimpleMetaStoreUtil.retrieveMetaFolder(parentFolder, DISK_USAGE_FOLDER);
		} catch (CoreException e) {
			logger.error("Cannot initialize disk usage data file", e); //$NON-NLS-1$
			return null;
		}
	}
}
