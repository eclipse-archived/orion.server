/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.server.gerritfs;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
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

@Export("/list/*")
@Singleton
public class GerritListFile extends HttpServlet {
	private final GitRepositoryManager repoManager;
	private final ProjectControl.Factory projControlFactory;
	private final Provider<WebSession> session;
	private final AccountCache accountCache;
	private final Config config;
	private final AccountManager accountManager;
	
	private static Logger log = LoggerFactory
			.getLogger(GerritListFile.class);

	@Inject
	public GerritListFile(final GitRepositoryManager repoManager, final ProjectControl.Factory project, Provider<WebSession> session, AccountCache accountCache,
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
		handleAuth(req);
		resp.setCharacterEncoding("UTF-8");
		final PrintWriter out = resp.getWriter();
		try {
			String pathInfo = req.getPathInfo();
			Pattern pattern = Pattern.compile("/([^/]*)(?:/([^/]*)(?:/(.*))?)?");
			Matcher matcher = pattern.matcher(pathInfo);
			matcher.matches();
			String projectName = null;
			String refName = null;
			String filePath = null;
			if (matcher.groupCount() > 0) {
				projectName = matcher.group(1);
				refName = matcher.group(2);
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
				if (filePath == null)
					filePath = "";
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
				}
				Repository repo = repoManager.openRepository(projName);
				if (refName == null) {
					ArrayList<HashMap<String, Object>> contents = new ArrayList<HashMap<String, Object>>();
					List<Ref> call;
					try {
						call = new Git(repo).branchList().call();
						Git git = new Git(repo);
						for (Ref ref : call) {
							HashMap<String, Object> jsonObject = new HashMap<String, Object>();
							jsonObject.put("name", ref.getName());
							jsonObject.put("type", "ref");
							jsonObject.put("size", "0");
							jsonObject.put("path", "");
							jsonObject.put("project", projectName);
							jsonObject.put("ref", ref.getName());
							lastCommit(git, null, ref.getObjectId(), jsonObject);
							contents.add(jsonObject);
						}
						String response = JSONUtil.write(contents);
						resp.setContentType("application/json");
						resp.setHeader("Cache-Control", "no-cache");
						resp.setHeader("ETag", "W/\"" + response.length() + "-" + response.hashCode() + "\"");
						log.debug(response);
						out.write(response);
					} catch (GitAPIException e) {
					}
				} else {
					Ref head = repo.getRef(refName);
					if (head == null) {
						ArrayList<HashMap<String, String>> contents = new ArrayList<HashMap<String, String>>();
						String response = JSONUtil.write(contents);
						resp.setContentType("application/json");
						resp.setHeader("Cache-Control", "no-cache");
						resp.setHeader("ETag", "W/\"" + response.length() + "-" + response.hashCode() + "\"");
						log.debug(response);
						out.write(response);
						return;
					}
					RevWalk walk = new RevWalk(repo);
					// add try catch to catch failures
					Git git = new Git(repo);
					RevCommit commit = walk.parseCommit(head.getObjectId());
					RevTree tree = commit.getTree();
					TreeWalk treeWalk = new TreeWalk(repo);
					treeWalk.addTree(tree);
					treeWalk.setRecursive(false);
					if (!filePath.equals("")) {
						PathFilter pathFilter = PathFilter.create(filePath);
						treeWalk.setFilter(pathFilter);
					}
					if (!treeWalk.next()) {
						CanonicalTreeParser canonicalTreeParser = treeWalk
								.getTree(0, CanonicalTreeParser.class);
						ArrayList<HashMap<String, Object>> contents = new ArrayList<HashMap<String, Object>>();
						if (canonicalTreeParser != null) {
							while (!canonicalTreeParser.eof()) {
								String path = canonicalTreeParser
										.getEntryPathString();
								FileMode mode = canonicalTreeParser
										.getEntryFileMode();
								listEntry(path, mode.equals(FileMode.TREE) ? "dir"
														: "file", "0", path, projectName, head.getName(), git, contents);
								canonicalTreeParser.next();
							}
						}
						String response = JSONUtil.write(contents);
						resp.setContentType("application/json");
						resp.setHeader("Cache-Control", "no-cache");
						resp.setHeader("ETag", "\"" + tree.getId().getName() + "\"");
						log.debug(response);
						out.write(response);
					} else {
						// if (treeWalk.isSubtree()) {
						// treeWalk.enterSubtree();
						// }
						ArrayList<HashMap<String, Object>> contents = new ArrayList<HashMap<String, Object>>();
						do {
							if (treeWalk.isSubtree()) {
								String test = new String(treeWalk.getRawPath());
								if (test.length() /*treeWalk.getPathLength()*/ > filePath
										.length()) {
									listEntry(treeWalk.getNameString(), "dir", "0", treeWalk.getPathString(), projectName, head.getName(), git, contents);
								}
								if (test.length() /*treeWalk.getPathLength()*/ <= filePath
										.length()) {
									treeWalk.enterSubtree();
								}
							} else {
								ObjectId objId = treeWalk.getObjectId(0);
								ObjectLoader loader = repo.open(objId);
								long size = loader.getSize();
								listEntry(treeWalk.getNameString(), "file", Long.toString(size), treeWalk.getPathString(), projectName, head.getName(), git, contents);
							}
						} while (treeWalk.next());
						String response = JSONUtil.write(contents);
						resp.setContentType("application/json");
						resp.setHeader("Cache-Control", "no-cache");
						resp.setHeader("ETag", "\"" + tree.getId().getName() + "\"");
						log.debug(response);
						out.write(response);
					}
					walk.release();
					treeWalk.release();
				}
			}
		} catch (RepositoryNotFoundException e) {
			handleException(resp, e, 400);
		} catch (MissingObjectException e) {
			// example "Missing unknown 7035305927ca125757ecd8407e608f6dcf0bd8a5"
			// usually indicative of being unable to locate a commit from a submodule
			log.error(e.getMessage(), e);
			String msg = e.getMessage() + ".  This exception could have been caused by the use of a git submodule, " +
					"which is currently not supported by the repository browser";
			handleException(resp, new Exception(msg), 501);
		} catch (IOException e) {
			handleException(resp, e, 500);
		} finally {
			out.close();
		}
	}
	
	private void listEntry(String name, String type, String size, String path, String projectName, String ref, Git git,
			ArrayList<HashMap<String, Object>> contents) {
		HashMap<String, Object> jsonObject = new HashMap<String, Object>();
		jsonObject.put("name", name);
		jsonObject
				.put("type", type);
		jsonObject.put("size", size);
		jsonObject.put("path", path);
		jsonObject.put("project", projectName);
		jsonObject.put("ref", ref);
		//if (type.equals("dir")) {
			lastCommit(git, path, null, jsonObject);
		//}
		contents.add(jsonObject);
	}

	private void lastCommit(Git git, String path, AnyObjectId revId,
			HashMap<String, Object> jsonObject) {
		HashMap<String, Object> latestCommitObj = new HashMap<String, Object>();
		HashMap<String, String> authorObj = new HashMap<String, String>();
		HashMap<String, String> committerObj = new HashMap<String, String>();
		Iterable<RevCommit> log = null;
		try {
			if (path != null) {
				log = git.log().addPath(path).setMaxCount(1).call();
			} else if (revId != null) {
				log = git.log().add(revId).setMaxCount(1).call();
			}
			Iterator<RevCommit> it = log.iterator();
			while (it.hasNext()) {
				RevCommit rev = (RevCommit) it.next();
				PersonIdent committer = rev.getCommitterIdent();
				committerObj.put("Name", committer.getName());
				committerObj.put("Email", committer.getEmailAddress());
				committerObj.put("Date", committer.getWhen().toString());
				
				PersonIdent author = rev.getAuthorIdent();
				authorObj.put("Name", author.getName());
				String authorEmail = author.getEmailAddress();
				authorObj.put("Email", authorEmail);
				authorObj.put("Date", author.getWhen().toString());
				
				latestCommitObj.put("Author", authorObj);
				latestCommitObj.put("Committer", committerObj);
				latestCommitObj.put("Message", rev.getFullMessage());
				latestCommitObj.put("SHA1", rev.getId().getName());
				latestCommitObj.put("AvatarURL", getImageLink(authorEmail));
				
				jsonObject.put("LastCommit", latestCommitObj);
			}
		} catch (GitAPIException e) {
		} catch (MissingObjectException e) {
		} catch (IncorrectObjectTypeException e) {
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
	
	private void handleException(HttpServletResponse resp, Exception e, int status) throws IOException {
		log.error(e.getMessage());
		PrintWriter out = resp.getWriter();
		HashMap<String, Object> jsonObject = new HashMap<String, Object>();
		jsonObject.put("Severity", "Error");
		jsonObject.put("Message", e.getMessage());
		String response = JSONUtil.write(jsonObject);
		resp.setStatus(status);
		resp.setContentType("application/json");
		out.write(response);
		out.flush();
	}
	
	public static String getImageLink(String emailAddress) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
		} catch (NoSuchAlgorithmException e) {
			//without MD5 we can't compute gravatar hashes
			return null;
		}
		digest.update(emailAddress.trim().toLowerCase().getBytes());
		byte[] digestValue = digest.digest();
		StringBuffer result = new StringBuffer("https://www.gravatar.com/avatar/"); //$NON-NLS-1$
		for (int i = 0; i < digestValue.length; i++) {
			String current = Integer.toHexString((digestValue[i] & 0xFF));
			//left pad with zero
			if (current.length() == 1)
				result.append('0');
			result.append(current);
		}
		//Default to "mystery man" icon if the user has no gravatar, and use a 40 pixel image
		result.append("?d=mm"); //$NON-NLS-1$
		return result.toString();
	}

}
