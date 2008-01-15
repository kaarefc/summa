/* $Id: SSHDeployerTest.java,v 1.6 2007/10/11 12:56:25 te Exp $
 * $Revision: 1.6 $
 * $Date: 2007/10/11 12:56:25 $
 * $Author: te $
 *
 * The Summa project.
 * Copyright (C) 2005-2007  The State and University Library
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package dk.statsbiblioteket.summa.score.server.deploy;

import java.io.File;
import java.io.IOException;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.summa.common.configuration.storage.MemoryStorage;
import dk.statsbiblioteket.summa.score.feedback.ConsoleFeedback;
import dk.statsbiblioteket.util.qa.QAInfo;

/**
 * SSHDeployer Tester.
 *
 * This unit-test is somewhat special, as it requires manuel activation.
 * This is because it was too large a task to make a fake SSH-server.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "mke")
public class SSHDeployerTest extends TestCase {
    public static final String PROPERTY_LOGIN =
            "te@pc990.sb";

    public static final String PROPERTY_SOURCE =
            "ClientManager/test/dk/statsbiblioteket/summa/score/server/deploy/FakeZIP.zip";
    public static final String PROPERTY_DESTINATION =
            "/tmp/fakeClient";
    public static final String PROPERTY_START_CONFSERVER =
            "NA";
    public static final String PROPERTY_CLIENT_INSTANCEID =
            "fake client-01";

    private static final String dest = PROPERTY_DESTINATION + "/FakeZIP.zip";
    private static final String jar = PROPERTY_DESTINATION + "/FakeJAR.jar";
    private static final String output = PROPERTY_DESTINATION + "/output.txt";

    public SSHDeployerTest(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        if (!new File(PROPERTY_SOURCE).exists()) {
            throw new IOException("The test-package " + PROPERTY_SOURCE
                                  + " should be at "
                                  + new File(PROPERTY_SOURCE).getAbsoluteFile().
                                    getParent() + ". It can be build with "
                                  + "the script maketestpackage.sh");
        }
        super.setUp();
    }

    public void tearDown() throws Exception {
        String[] deletables = new String[]{dest, jar, output};
        for (String deletable: deletables) {
            if (new File(deletable).exists()) {
                new File(deletable).delete();
            }
        }
        super.tearDown();
    }

    public static Test suite() {
        return new TestSuite(SSHDeployerTest.class);
    }

    public void testException() throws Exception {
        SSHDeployer deployer =
                new SSHDeployer(new Configuration(new MemoryStorage()));
        try {
            deployer.deploy(new ConsoleFeedback());
            fail("The deployer should throw an exception when it could not "
                 + "find the right properties");
        } catch (Exception e) {
            // Expected behaviour
        }
    }

    private Configuration makeConfiguration() {
        MemoryStorage storage = new MemoryStorage();
        storage.put(SSHDeployer.DEPLOYER_BUNDLE_PROPERTY, PROPERTY_SOURCE);
        storage.put(SSHDeployer.BASEPATH_PROPERTY, PROPERTY_DESTINATION);
        storage.put(SSHDeployer.DEPLOYER_TARGET_PROPERTY, PROPERTY_LOGIN);
        storage.put(SSHDeployer.CLIENT_CONF_PROPERTY, PROPERTY_START_CONFSERVER);
        new FakeThinClient();
        return new Configuration(storage);
    }

    public void doDeploy() throws Exception {
        assertTrue("The source " + new File(PROPERTY_SOURCE).getAbsoluteFile()
                   + " should exist", new File(PROPERTY_SOURCE).exists());
        assertFalse("The file " + dest
                   + " should not exist before deploy",
                   new File(dest).exists());
        Configuration configuration = makeConfiguration();
        SSHDeployer deployer = new SSHDeployer(configuration);
        deployer.deploy(new ConsoleFeedback());
        assertTrue("The file " + dest
                   + " should exist after deploy",
                   new File(dest).exists());
        assertTrue("The file " + jar
                   + " should exist after deploy",
                   new File(jar).exists());
    }

    public void doStart() throws Exception {
        assertTrue("The source " + new File(PROPERTY_SOURCE).getAbsoluteFile()
                   + " should exist", new File(PROPERTY_SOURCE).exists());
        Configuration configuration = makeConfiguration();
        SSHDeployer deployer = new SSHDeployer(configuration);
        deployer.deploy(new ConsoleFeedback());
        assertTrue("The file " + jar
                   + " should exist after deploy",
                   new File(jar).exists());

        assertFalse("The file " + output
                   + " should not exist before start",
                   new File(output).exists());
        deployer.start(new ConsoleFeedback());
        assertTrue("The file " + output
                   + " should exist after start",
                   new File(output).exists());
    }
}
