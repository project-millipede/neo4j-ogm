/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with
 * separate copyright notices and license terms. Your use of the source
 * code for these subcomponents is subject to the terms and
 *  conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package org.neo4j.ogm.drivers;

import static org.junit.Assert.*;

import org.junit.Test;
import org.neo4j.ogm.authentication.Credentials;
import org.neo4j.ogm.authentication.UsernamePasswordCredentials;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.config.DriverConfiguration;

/**
 * @author vince
 */

public class DriverConfigurationTest {

    @Test
    public void shouldLoadHttpDriverConfigFromPropertiesFile() {
        DriverConfiguration driverConfig = new DriverConfiguration(new Configuration("http.driver.properties"));
        assertEquals("http://neo4j:password@localhost:7474", driverConfig.getURI());
    }

    @Test
    public void shouldLoadEmbeddedDriverConfigFromPropertiesFile() {
        DriverConfiguration driverConfig = new DriverConfiguration(new Configuration("embedded.driver.properties"));
        assertEquals("file:///var/tmp/neo4j.db", driverConfig.getURI());
    }

    @Test
    public void shouldLoadBoltDriverConfigFromPropertiesFile() {
        DriverConfiguration driverConfig = new DriverConfiguration(new Configuration("bolt.driver.properties"));
        assertEquals("bolt://neo4j:password@localhost", driverConfig.getURI());
        assertEquals(Integer.valueOf(150), driverConfig.getConnectionPoolSize());
        assertEquals("NONE", driverConfig.getEncryptionLevel());
        assertEquals("TRUST_ON_FIRST_USE", driverConfig.getTrustStrategy());
        assertEquals("/tmp/cert", driverConfig.getTrustCertFile());
    }

    @Test
    public void shouldSetUsernameAndPasswordCredentialsForBoltProtocol() {
        String username = "neo4j";
        String password = "password";
        Configuration dbConfig = new Configuration();
        dbConfig.driverConfiguration().setURI("bolt://" + username + ":" + password + "@localhost");
        Credentials credentials = dbConfig.driverConfiguration().getCredentials();
        UsernamePasswordCredentials basic = (UsernamePasswordCredentials) credentials;
        assertNotNull(basic);
        assertEquals(username, basic.getUsername());
        assertEquals(password, basic.getPassword());
    }

    @Test
	public void shouldLoadEmbeddedHaPropertiesFromRawConfiguration() {
		Configuration config = new Configuration("neo4j-ha.properties");
		assertEquals("1", config.get("ha.server_id"));
		assertEquals("localhost:5001", config.get("ha.initial_hosts"));
		assertEquals("true", config.get("ha.allow_init_cluster"));
	}

	@Test
	public void shouldGetNeo4jHaPropertiesFileFromDriverConfiguration() {
		DriverConfiguration config = new DriverConfiguration(new Configuration("embedded.ha.driver.properties"));
		assertEquals("neo4j-ha.properties", config.getNeo4jHaPropertiesFile());
	}

    @Test
    public void testURISInConfiguration() throws Exception {
        DriverConfiguration config = new DriverConfiguration(new Configuration("ogm-bolt-uris.properties"));
        assertArrayEquals(new String[] {"bolt://localhost:9998", "bolt://localhost:9999"}, config.getURIS());
    }
}
