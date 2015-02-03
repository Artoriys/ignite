/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.rest;

import org.apache.ignite.configuration.*;
import org.apache.ignite.internal.*;
import org.apache.ignite.internal.client.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.spi.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.testframework.*;
import org.apache.ignite.testframework.junits.common.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.*;

/**
 *
 */
public class RestProcessorStartSelfTest extends GridCommonAbstractTest {
    /** */
    private static final TcpDiscoveryIpFinder IP_FINDER = new TcpDiscoveryVmIpFinder(true);

    /** */
    private static final String HOST = "127.0.0.1";

    /** */
    public static final int TCP_PORT = 11222;

    /** */
    private CountDownLatch gridReady;

    /** */
    private CountDownLatch proceed;

    /** {@inheritDoc}*/
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        cfg.setLocalHost(HOST);

        assert cfg.getClientConnectionConfiguration() == null;

        ClientConnectionConfiguration clientCfg = new ClientConnectionConfiguration();

        clientCfg.setRestTcpPort(TCP_PORT);

        cfg.setClientConnectionConfiguration(clientCfg);

        TestDiscoverySpi disc = new TestDiscoverySpi();

        disc.setIpFinder(IP_FINDER);

        cfg.setDiscoverySpi(disc);

        return cfg;
    }

    /** {@inheritDoc}*/
    @Override protected void beforeTest() throws Exception {
        gridReady = new CountDownLatch(1);
        proceed = new CountDownLatch(1);
    }

    /** {@inheritDoc}*/
    @Override protected void afterTest() throws Exception {
        stopAllGrids();
    }

    /**
     *  @throws Exception If failed.
     */
    public void testTcpStart() throws Exception {
        GridClientConfiguration clCfg = new GridClientConfiguration();

        clCfg.setProtocol(GridClientProtocol.TCP);
        clCfg.setServers(Collections.singleton(HOST + ":" + TCP_PORT));

        doTest(clCfg);
    }

    /**
     * @param cfg Client configuration.
     * @throws Exception If failed.
     */
    private void doTest(final GridClientConfiguration cfg) throws Exception {
        GridTestUtils.runAsync(new IgniteCallable<Object>() {
            @Override public Object call() {
                try {
                    startGrid();
                }
                catch (Exception e) {
                    log().error("Grid start failed", e);

                    fail();
                }

                return null;
            }
        });

        try {
            gridReady.await();

            IgniteInternalFuture<GridClient> c = GridTestUtils.runAsync(new Callable<GridClient>() {
                @Override public GridClient call() throws Exception {
                    return GridClientFactory.start(cfg);
                }
            });

            try {
                proceed.countDown();

                c.get().compute().refreshTopology(false, false);
            }
            finally {
                GridClientFactory.stopAll();
            }
        }
        catch (Throwable e) {
            e.printStackTrace();
        }
        finally {
            proceed.countDown();
        }
    }

    /**
     * Test SPI.
     */
    private class TestDiscoverySpi extends TcpDiscoverySpi {
        /** {@inheritDoc} */
        @Override public void spiStart(@Nullable String gridName) throws IgniteSpiException {
            gridReady.countDown();

            try {
                proceed.await();
            }
            catch (InterruptedException e) {
                throw new IgniteSpiException("Failed to await start signal.", e);
            }

            super.spiStart(gridName);
        }
    }
}
