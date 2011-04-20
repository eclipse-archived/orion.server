/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class GitFetchTest extends GitTest {
	@Test
	public void testFetch() throws IOException, SAXException, JSONException {
		// add clone
		String contentLocation = clone(null);

		// link
		JSONObject project = linkProject(contentLocation, getMethodName());
		JSONObject gitSection = project.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection);
		String gitRemoteUri = gitSection.getString(GitConstants.KEY_REMOTE);

		// list remotes
		WebRequest request = GitRemoteTest.getGetGitRemoteRequest(gitRemoteUri);
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());
		JSONObject remotes = new JSONObject(response.getText());
		JSONArray remotesArray = remotes.getJSONArray(ProtocolConstants.KEY_CHILDREN);
		assertEquals(1, remotesArray.length());
		JSONObject remote = remotesArray.getJSONObject(0);
		assertNotNull(remote);
		assertEquals(Constants.DEFAULT_REMOTE_NAME, remote.getString(ProtocolConstants.KEY_NAME));
		String remoteLocation = remote.getString(ProtocolConstants.KEY_LOCATION);
		assertNotNull(remoteLocation);

		// get remote details
		JSONObject details = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER);
		String refId = details.getString(ProtocolConstants.KEY_ID);
		String remoteBranchLocation = details.getString(ProtocolConstants.KEY_LOCATION);

		// fetch
		fetch(remoteBranchLocation);

		// get remote details again
		String newRefId = getRemoteBranch(gitRemoteUri, 1, 0, Constants.MASTER).getString(ProtocolConstants.KEY_ID);
		// nothing new
		assertEquals(refId, newRefId);
	}

	@Test
	public void testPushAndFetch() throws IOException, SAXException, JSONException, URISyntaxException, JGitInternalException, GitAPIException {
		// clone1: create
		String contentLocation1 = clone(null);

		// clone1: link
		JSONObject project1 = linkProject(contentLocation1, getMethodName() + "1");
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);

		// clone2: create
		String contentLocation2 = clone(null);

		// clone2: link
		JSONObject project2 = linkProject(contentLocation2, getMethodName() + "2");
		String projectId2 = project2.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);
		String gitIndexUri2 = gitSection2.getString(GitConstants.KEY_INDEX);
		String gitCommitUri2 = gitSection2.getString(GitConstants.KEY_COMMIT);

		// clone2: change
		WebRequest request = getPutFileRequest(projectId2 + "/test.txt", "incoming change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri2);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri2, "incoming change commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone2: push
		JSONObject push = push(gitRemoteUri2, Constants.HEAD);
		Status result = Status.valueOf(push.getString(GitConstants.KEY_RESULT));
		assertEquals(Status.OK, result);

		JSONObject details = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
		String refId1 = details.getString(ProtocolConstants.KEY_ID);
		String remoteBranchLocation = details.getString(ProtocolConstants.KEY_LOCATION);

		// clone1: fetch
		fetch(remoteBranchLocation);

		details = getRemoteBranch(gitRemoteUri1, 1, 0, Constants.MASTER);
		String newRefId1 = details.getString(ProtocolConstants.KEY_ID);
		assertFalse(newRefId1.equals(refId1));

		// clone1: log master..origin/master
		// TODO replace with tests methods from GitLogTest, bug 340051
		Repository db1 = getRepositoryForContentLocation(contentLocation1);
		ObjectId master = db1.resolve(Constants.MASTER);
		ObjectId originMaster = db1.resolve(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + '/' + Constants.MASTER);
		Git git = new Git(db1);
		Iterable<RevCommit> commits = git.log().addRange(master, originMaster).call();
		int c = 0;
		for (RevCommit commit : commits) {
			assertEquals("incoming change commit", commit.getFullMessage());
			c++;
		}
		// a single incoming commit
		assertEquals(1, c);
	}

	@Test
	@Ignore("see bug 342727")
	public void testFetchSingleBranch() throws JSONException, IOException, SAXException, URISyntaxException, JGitInternalException, GitAPIException {

		// clone1
		String contentLocation1 = clone(null);

		// clone1: link
		JSONObject project1 = linkProject(contentLocation1, getMethodName() + "1");
		String projectId1 = project1.getString(ProtocolConstants.KEY_ID);
		JSONObject gitSection1 = project1.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection1);
		String gitRemoteUri1 = gitSection1.getString(GitConstants.KEY_REMOTE);
		String gitIndexUri1 = gitSection1.getString(GitConstants.KEY_INDEX);
		String gitCommitUri1 = gitSection1.getString(GitConstants.KEY_COMMIT);

		// clone1: branch 'a'
		FileRepository db1 = new FileRepository(new File(URIUtil.toFile(new URI(contentLocation1)), Constants.DOT_GIT));
		Git git1 = new Git(db1);
		Ref aBranch = git1.branchCreate().setName("a").call();
		assertEquals(Constants.R_HEADS + "a", aBranch.getName());

		// clone1: push all
		pushAll(contentLocation1);

		// clone2
		String contentLocation2 = clone(null);
		// XXX: checked out 'a'

		// clone2:  link
		JSONObject project2 = linkProject(contentLocation2, getMethodName() + "2");
		JSONObject gitSection2 = project2.optJSONObject(GitConstants.KEY_GIT);
		assertNotNull(gitSection2);
		String gitRemoteUri2 = gitSection2.getString(GitConstants.KEY_REMOTE);

		// clone1: switch to 'a'
		GitRemoteTest.ensureOnBranch(git1, "a");

		// clone1: change
		WebRequest request = getPutFileRequest(projectId1 + "/test.txt", "a change");
		WebResponse response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri1, "incoming a commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		JSONObject push = push(gitRemoteUri1, 2, 0, "a", Constants.HEAD);
		Status result = Status.valueOf(push.getString(GitConstants.KEY_RESULT));
		assertEquals(Status.OK, result);

		// clone1: switch to 'master'
		GitRemoteTest.ensureOnBranch(git1, Constants.MASTER);

		// clone1: change
		request = getPutFileRequest(projectId1 + "/test.txt", "master change");
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: add
		request = GitAddTest.getPutGitIndexRequest(gitIndexUri1);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: commit
		request = GitCommitTest.getPostGitCommitRequest(gitCommitUri1, "incoming master commit", false);
		response = webConversation.getResponse(request);
		assertEquals(HttpURLConnection.HTTP_OK, response.getResponseCode());

		// clone1: push
		push(gitRemoteUri1, 2, 0, Constants.MASTER, Constants.HEAD);

		// clone2: get remote details
		// XXX: checked out 'a'
		JSONObject aDetails = getRemoteBranch(gitRemoteUri2, 2, 0, "a");
		String aBranchLocation = aDetails.getString(ProtocolConstants.KEY_LOCATION);
		JSONObject masterDetails = getRemoteBranch(gitRemoteUri2, 2, 1, Constants.MASTER);
		String masterOldRefId = masterDetails.getString(ProtocolConstants.KEY_ID);

		// clone2: fetch 'a'
		fetch(aBranchLocation);

		// clone2: assert nothing new on 'master'
		masterDetails = getRemoteBranch(gitRemoteUri2, 2, 1, Constants.MASTER);
		String newRefId = masterDetails.getString(ProtocolConstants.KEY_ID);
		assertEquals(masterOldRefId, newRefId);
	}

	static WebRequest getPostGitRemoteRequest(String location, boolean fetch) throws JSONException {
		String requestURI;
		if (location.startsWith("http://"))
			requestURI = location;
		else
			requestURI = SERVER_LOCATION + GIT_SERVLET_LOCATION + GitConstants.REMOTE_RESOURCE + location;

		JSONObject body = new JSONObject();
		body.put(GitConstants.KEY_FETCH, Boolean.toString(fetch));
		InputStream in = new StringBufferInputStream(body.toString());
		WebRequest request = new PostMethodWebRequest(requestURI, in, "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		setAuthentication(request);
		return request;
	}
}
