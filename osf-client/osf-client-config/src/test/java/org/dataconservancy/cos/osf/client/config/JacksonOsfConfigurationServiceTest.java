/*
 * Copyright 2016 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.cos.osf.client.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for the Jackson-based configuration service.  Note that the same configuration file can contain
 * both the Waterbutler and OSF configurations in separate JSON objects.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class JacksonOsfConfigurationServiceTest {

    @Test
    public void testOsf() throws Exception {
        final JacksonOsfConfigurationService underTest =
                new JacksonOsfConfigurationService(
                        "org/dataconservancy/cos/osf/client/config/osf-client-jacksontest.json");

        final OsfClientConfiguration config = underTest.getConfiguration();
        assertNotNull(config);

        assertEquals("192.168.99.100", config.getHost());
        assertEquals(8000, config.getPort());
        assertEquals("/v2/", config.getBasePath());
        assertEquals("foo", config.getAuthHeader());
        assertEquals(20 * 1000, config.connect_timeout_ms);
        assertEquals("2.2", config.getApiVersion());
    }

    @Test
    public void testWb() throws Exception {
        final JacksonWbConfigurationService underTest = new JacksonWbConfigurationService(
                "org/dataconservancy/cos/osf/client/config/osf-client-jacksontest.json");

        final WbClientConfiguration config = underTest.getConfiguration();
        assertNotNull(config);

        assertEquals("192.168.99.100", config.getHost());
        assertEquals(7777, config.getPort());
        assertEquals("/v1/", config.getBasePath());
        assertEquals(10 * 1000, config.connect_timeout_ms);
    }
}
