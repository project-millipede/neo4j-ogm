/*
 * Copyright (c) 2002-2017 "Neo Technology,"
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

package org.neo4j.ogm.drivers.bolt.driver;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.neo4j.driver.v1.*;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.neo4j.driver.v1.exceptions.ServiceUnavailableException;
import org.neo4j.ogm.authentication.UsernamePasswordCredentials;
import org.neo4j.ogm.config.DriverConfiguration;
import org.neo4j.ogm.driver.AbstractConfigurableDriver;
import org.neo4j.ogm.drivers.bolt.request.BoltRequest;
import org.neo4j.ogm.drivers.bolt.transaction.BoltTransaction;
import org.neo4j.ogm.exception.ConnectionException;
import org.neo4j.ogm.request.Request;
import org.neo4j.ogm.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Vince Bickers
 * @author Luanne Misquitta
 * @author Mark Angrish
 */
public class BoltDriver extends AbstractConfigurableDriver {

	private final Logger LOGGER = LoggerFactory.getLogger(BoltDriver.class);

	private Driver boltDriver;

	// required for service loader mechanism
	public BoltDriver() {
	}

	public BoltDriver(DriverConfiguration driverConfiguration) {

		configure(driverConfiguration);
	}

	public BoltDriver(Driver boltDriver) {
		this.boltDriver = boltDriver;
	}

	@Override
	public void configure(DriverConfiguration config) {

		close();

		super.configure(config);

		Config driverConfig = buildDriverConfig(config);

		if (config.getCredentials() != null) {
			UsernamePasswordCredentials credentials = (UsernamePasswordCredentials) config.getCredentials();
			AuthToken authToken = AuthTokens.basic(credentials.getUsername(), credentials.getPassword());
			boltDriver = createDriver(config, driverConfig, authToken);
		} else {
			try {
				boltDriver = createDriver(config, driverConfig, AuthTokens.none());
			} catch (ServiceUnavailableException e) {
				throw new ConnectionException("Could not create driver instance.", e);
			}
			LOGGER.debug("Bolt Driver credentials not supplied");
		}
	}

	private Driver createDriver(DriverConfiguration config, Config driverConfig, AuthToken authToken) {
		if (config.getURIS() == null) {
			return GraphDatabase.driver(config.getURI(), authToken, driverConfig);
		} else {
			List<String> uris = new ArrayList<>();
			uris.add(config.getURI());
			uris.addAll(Arrays.asList(config.getURIS()));

			// OGM 2.1.x depends on driver 1.2.x, higher driver versions provide
			// GraphDatabase.driver(Iterable<String> uri, ..)
			for (String uri : uris) {
				try {
					Driver driver = GraphDatabase.driver(uri, authToken, driverConfig);
					LOGGER.info("Driver initialised with URI={}", uri);
					return driver;
				}
				catch (ServiceUnavailableException ex) {
					LOGGER.warn("Failed to initialise driver with URI={}", uri);
					LOGGER.debug("Driver initialisation for URI={} failed exception", uri, ex);
					// This URI failed, so loop around again if we have another.
				}
			}
			throw new ServiceUnavailableException("No valid database URI found");
		}
	}

	@Override
	public Transaction newTransaction(Transaction.Type type, String bookmark) {
		Session session = newSession(type); //A bolt session can have at most one transaction running at a time
		return new BoltTransaction(transactionManager, nativeTransaction(session, bookmark), session, type);
	}

	@Override
	public synchronized void close() {
		if (boltDriver != null) {
			try {
				LOGGER.info("Shutting down Bolt driver {} ", boltDriver);
				boltDriver.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public Request request() {
		return new BoltRequest(transactionManager);
	}

	private Session newSession(Transaction.Type type) {
		Session boltSession;
		try {
			boltSession = boltDriver.session(type.equals(Transaction.Type.READ_ONLY) ? AccessMode.READ : AccessMode.WRITE);
		} catch (ClientException ce) {
			throw new ConnectionException("Error connecting to graph database using Bolt: " + ce.neo4jErrorCode() + ", " + ce.getMessage(), ce);
		} catch (Exception e) {
			throw new ConnectionException("Error connecting to graph database using Bolt", e);
		}
		return boltSession;
	}

	private org.neo4j.driver.v1.Transaction nativeTransaction(Session session, String bookmark) {

		org.neo4j.driver.v1.Transaction nativeTransaction;

		Transaction tx = transactionManager.getCurrentTransaction();
		if (tx != null) {
			LOGGER.debug("Using current transaction: {}", tx);
			nativeTransaction = ((BoltTransaction) tx).nativeBoltTransaction();
		} else {
			if (bookmark != null) {
				LOGGER.debug("No current transaction, starting a new one with bookmark [{}]", bookmark);
				nativeTransaction = session.beginTransaction(bookmark);
			} else {
				LOGGER.debug("No current transaction, starting a new one");
				nativeTransaction = session.beginTransaction();
			}
		}
		LOGGER.debug("Native transaction: {}", nativeTransaction);
		return nativeTransaction;
	}

	private BoltConfig getBoltConfiguration(DriverConfiguration driverConfiguration) {
		BoltConfig boltConfig = new BoltConfig();

		if (driverConfiguration.getEncryptionLevel() != null) {
			try {
				boltConfig.encryptionLevel = Config.EncryptionLevel.valueOf(driverConfiguration.getEncryptionLevel().toUpperCase());
			} catch (IllegalArgumentException iae) {
				LOGGER.debug("Invalid configuration for the Bolt Driver Encryption Level: {}", driverConfiguration.getEncryptionLevel());
				throw iae;
			}
		}

		if (driverConfiguration.getConnectionPoolSize() != null) {
			boltConfig.sessionPoolSize = driverConfiguration.getConnectionPoolSize();
		}

		if (driverConfiguration.getTrustStrategy() != null) {
			try {
				boltConfig.trustStrategy = Config.TrustStrategy.Strategy.valueOf(driverConfiguration.getTrustStrategy());
			} catch (IllegalArgumentException iae) {
				LOGGER.debug("Invalid configuration for the Bolt Driver Trust Strategy: {}", driverConfiguration.getTrustStrategy());
				throw iae;
			}
		}

		if (driverConfiguration.getTrustCertFile() != null) {
			boltConfig.trustCertFile = driverConfiguration.getTrustCertFile();
		}

		if (driverConfiguration.getConnectionLivenessCheckTimeout() != null) {
			boltConfig.connectionLivenessCheckTimeout = driverConfiguration.getConnectionLivenessCheckTimeout();
		}

		return boltConfig;
	}

	private Config buildDriverConfig(DriverConfiguration driverConfig) {
		try {
			BoltConfig boltConfig = getBoltConfiguration(driverConfig);
			Config.ConfigBuilder configBuilder = Config.build();
			configBuilder.withMaxSessions(boltConfig.sessionPoolSize);
			configBuilder.withEncryptionLevel(boltConfig.encryptionLevel);
			if (boltConfig.connectionLivenessCheckTimeout != null) {
				configBuilder.withConnectionLivenessCheckTimeout(boltConfig.connectionLivenessCheckTimeout, TimeUnit.MILLISECONDS);
			}
			if (boltConfig.trustStrategy != null) {
				if (boltConfig.trustCertFile == null) {
					throw new IllegalArgumentException("Missing configuration value for trust.certificate.file");
				}
				if (boltConfig.trustStrategy.equals(Config.TrustStrategy.Strategy.TRUST_ON_FIRST_USE)) {
					configBuilder.withTrustStrategy(Config.TrustStrategy.trustOnFirstUse(new File(new URI(boltConfig.trustCertFile))));
				}
				if (boltConfig.trustStrategy.equals(Config.TrustStrategy.Strategy.TRUST_SIGNED_CERTIFICATES)) {
					configBuilder.withTrustStrategy(Config.TrustStrategy.trustSignedBy(new File(new URI(boltConfig.trustCertFile))));
				}
			}

			return configBuilder.toConfig();
		} catch (Exception e) {
			throw new ConnectionException("Unable to build driver configuration", e);
		}
	}

	class BoltConfig {

		public static final int DEFAULT_SESSION_POOL_SIZE = 50;
		Config.EncryptionLevel encryptionLevel = Config.EncryptionLevel.REQUIRED;
		int sessionPoolSize = DEFAULT_SESSION_POOL_SIZE;
		Config.TrustStrategy.Strategy trustStrategy;
		String trustCertFile;
		Integer connectionLivenessCheckTimeout;
	}
}
