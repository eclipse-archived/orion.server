package org.eclipse.orion.server.gerritfs;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Export("/gitcontents/*")
@Singleton
public class GerritGitFileContents  extends HttpServlet {
	private final GitRepositoryManager repoManager;
	private final ProjectControl.Factory projControlFactory;
	private final Provider<WebSession> session;
	private final AccountCache accountCache;
	private final Config config;
	private final AccountManager accountManager;
	
	private static Logger log = LoggerFactory
			.getLogger(GerritGitFileContents.class);
	@Inject
	public GerritGitFileContents(final GitRepositoryManager repoManager, final ProjectControl.Factory project, Provider<WebSession> session, AccountCache accountCache,
		    @GerritServerConfig Config config,
		    final AccountManager accountManager) {
		this.repoManager = repoManager;
		this.projControlFactory = project;
		this.session = session;
		this.accountCache = accountCache;
		this.config = config;
		this.accountManager = accountManager;
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		final ServletOutputStream out = resp.getOutputStream();
		handleAuth(req);
		try {
			String pathInfo = req.getPathInfo();
			Pattern pattern = Pattern.compile("/([^/]*)(?:/([^/]*)(?:/(.*))?)?");
			Matcher matcher = pattern.matcher(pathInfo);
			matcher.matches();
			String projectName = null;
			String refName = null;
			String filePath = null;
			if (matcher.groupCount() > 0) {
				refName = matcher.group(1);
				projectName = matcher.group(2);
				filePath = matcher.group(3);
				if (projectName == null || projectName.equals("")) {
					projectName = null;
				} else {
					projectName = java.net.URLDecoder.decode(projectName, "UTF-8");
				}
				if (refName == null || refName.equals("")) {
					refName = null;
				} else {
					refName = java.net.URLDecoder.decode(refName, "UTF-8");
				}
				if (filePath == null || filePath.equals("")) {
					filePath = null;
				} else {
					filePath = java.net.URLDecoder.decode(filePath, "UTF-8");
				}
			}
			if (projectName != null) {
				NameKey projName = NameKey.parse(projectName);
				ProjectControl control;
				try {
					control = projControlFactory.controlFor(projName);
					if (!control.isVisible()) {
						log.debug("Project not visible!");
						resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "You need to be logged in to see private projects");
						return;
					}
				} catch (NoSuchProjectException e1) {
					resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No such project exists.");
				}
			}
			if (projectName == null || refName == null || filePath == null) {
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "You need to provide a projectName, refName and filePath.");
				return;
			} else {
				NameKey projName = NameKey.parse(projectName);
				Repository repo = repoManager.openRepository(projName);
				ObjectId commitId = repo.resolve(refName);
				if (commitId != null){
					RevWalk walk = new RevWalk(repo);
					RevCommit commit = walk.parseCommit(commitId);
					RevTree tree = commit.getTree();
	
					TreeWalk treeWalk = new TreeWalk(repo);
					treeWalk.addTree(tree);
					treeWalk.setRecursive(true);
					treeWalk.setFilter(PathFilter.create(filePath));
					if (treeWalk.next()){
						ObjectId objId = treeWalk.getObjectId(0);
						ObjectLoader loader = repo.open(objId);
						loader.copyTo(out);
					} else {
						resp.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
					}
					resp.setHeader("ETag", "\"" + tree.getId().getName() + "\"");
					walk.release();
					treeWalk.release();
				} else {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
				}
				resp.setHeader("Cache-Control", "no-cache");
				resp.setContentType("application/javascript");
			}
		} finally {
			out.close();
		}
	}

	private void handleAuth(HttpServletRequest req) {
		String username = req.getRemoteUser();
		if (username != null) {
			 if (config.getBoolean("auth", "userNameToLowerCase", false)) {
			      username = username.toLowerCase(Locale.US);
		    }
			log.debug("User name: " + username);
		 	AccountState who = accountCache.getByUsername(username);
		 	log.debug("AccountState " + who);
			if (who == null && username.matches("^([a-zA-Z0-9][a-zA-Z0-9._-]*[a-zA-Z0-9]|[a-zA-Z0-9])$")) {
				log.debug("User is not registered with Gerrit. Register now."); // This approach assumes an auth type of HTTP_LDAP
				final AuthRequest areq = AuthRequest.forUser(username);
				try {
					accountManager.authenticate(areq);
					who = accountCache.getByUsername(username);
					if (who == null) {
						log.warn("Unable to register user \"" + username
								+ "\". Continue as anonymous.");
					} else {
						log.debug("User registered.");
					}
				} catch (AccountException e) {
					log.warn("Exception registering user \"" + username
							+ "\". Continue as anonymous.", e);
				}
			}
		 	if (who != null && who.getAccount().isActive()) {
		 		log.debug("Not anonymous user");
		 		WebSession ws = session.get();
		 		ws.setUserAccountId(who.getAccount().getId());
		 		ws.setAccessPathOk(AccessPath.REST_API, true);
		    } else {
		    	log.debug("Anonymous user");
		    }
		}
	}

}
