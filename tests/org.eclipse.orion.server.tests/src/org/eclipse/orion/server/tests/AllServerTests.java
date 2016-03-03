/*******************************************************************************
 * Copyright (c) 2010, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests;

import org.eclipse.orion.server.tests.cf.AllCFTests;
import org.eclipse.orion.server.tests.filters.ExcludedExtensionGzipFilterTest;
import org.eclipse.orion.server.tests.metastore.ProjectInfoTests;
import org.eclipse.orion.server.tests.metastore.SimpleMetaStoreConcurrencyTests;
import org.eclipse.orion.server.tests.metastore.SimpleMetaStoreLiveMigrationConcurrencyTests;
import org.eclipse.orion.server.tests.metastore.SimpleMetaStoreLiveMigrationTests;
import org.eclipse.orion.server.tests.metastore.SimpleMetaStoreWorkspaceProjectListConcurrencyTests;
import org.eclipse.orion.server.tests.metastore.SimpleMetaStoreTests;
import org.eclipse.orion.server.tests.metastore.SimpleMetaStoreUserPropertyCacheTests;
import org.eclipse.orion.server.tests.metastore.SimpleMetaStoreUtilTest;
import org.eclipse.orion.server.tests.metastore.SimpleMetaStoreWorkspacePropertyConcurrencyTests;
import org.eclipse.orion.server.tests.metastore.SimpleUserPasswordUtilTests;
import org.eclipse.orion.server.tests.metastore.UserInfoTests;
import org.eclipse.orion.server.tests.metastore.WorkspaceInfoTests;
import org.eclipse.orion.server.tests.prefs.PreferenceTest;
import org.eclipse.orion.server.tests.search.SearchTest;
import org.eclipse.orion.server.tests.servlets.files.AdvancedFilesTest;
import org.eclipse.orion.server.tests.servlets.files.CoreFilesTest;
import org.eclipse.orion.server.tests.servlets.git.AllGitTests;
import org.eclipse.orion.server.tests.servlets.site.AllSiteTests;
import org.eclipse.orion.server.tests.servlets.users.BasicUsersTest;
import org.eclipse.orion.server.tests.servlets.workspace.WorkspaceServiceTest;
import org.eclipse.orion.server.tests.servlets.xfer.TransferTest;
import org.eclipse.orion.server.tests.tasks.AllTaskTests;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Runs all automated server tests.
 */
@RunWith(Suite.class)
@SuiteClasses({AdvancedFilesTest.class, //
		AllCFTests.class,//
		AllSiteTests.class, //
		AllTaskTests.class, //
		Base64Test.class, //
		BasicUsersTest.class, //
		CoreFilesTest.class, //
		ExcludedExtensionGzipFilterTest.class, //
		MetaStoreTest.class, //
		PreferenceTest.class, //
		ProjectInfoTests.class, //
		SearchTest.class, //
		SimpleMetaStoreConcurrencyTests.class, //
		SimpleMetaStoreLiveMigrationTests.class, //
		SimpleMetaStoreLiveMigrationConcurrencyTests.class, //
		SimpleMetaStoreWorkspacePropertyConcurrencyTests.class, //
		SimpleMetaStoreTests.class, //
		SimpleMetaStoreUserPropertyCacheTests.class, //
		SimpleMetaStoreUtilTest.class, //
		SimpleUserPasswordUtilTests.class, //
		TransferTest.class, //
		UserInfoTests.class, //
		WorkspaceInfoTests.class, //
		WorkspaceServiceTest.class, //
		SimpleMetaStoreWorkspaceProjectListConcurrencyTests.class, //
		AllGitTests.class, //
})
public class AllServerTests {
	//goofy junit4, no class body needed
}
