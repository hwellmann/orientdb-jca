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
package eu.devexpert.orient.jca;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.resource.ResourceException;
import javax.resource.spi.ConfigProperty;
import javax.resource.spi.ConnectionDefinition;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ResourceAdapter;
import javax.security.auth.Subject;

import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.db.graph.OGraphDatabase;
import com.orientechnologies.orient.core.db.graph.OGraphDatabasePool;
import com.orientechnologies.orient.core.exception.OStorageException;

import eu.devexpert.orient.jca.api.OrientDBConnectionFactory;
import eu.devexpert.orient.jca.api.OrientDBGraph;
import eu.devexpert.orient.jca.api.OrientDBManagedConnection;
import eu.devexpert.orient.jca.api.OrientDBManagedConnectionFactory;

/**
 * 
 * @author Dumitru Ciubenco
 * @since 0.0.1
 * @created August 05, 2012
 */
@ConnectionDefinition(
	connectionFactory = OrientDBConnectionFactory.class, connectionFactoryImpl = OrientDBConnectionFactoryImpl.class, connection = OrientDBGraph.class,
	connectionImpl = OrientDBGraphImpl.class)
public class OrientDBManagedConnectionFactoryImpl implements OrientDBManagedConnectionFactory {
	private static final long		serialVersionUID	= 1L;
	private static Logger			logger				= Logger.getLogger(OrientDBManagedConnectionFactoryImpl.class.getName());
	private OrientDBResourceAdapter	ra;
	private PrintWriter				logwriter;
	private int						connectionsCreated	= 0;
	private OGraphDatabasePool		databasePool;

	@ConfigProperty(type = String.class, defaultValue = "local:../databases/temp-orientdb")
	private String					connectionUrl;
	@ConfigProperty(type = String.class, defaultValue = "admin")
	private String					username;
	@ConfigProperty(type = String.class, defaultValue = "admin")
	private String					password;
	@ConfigProperty(defaultValue = "true")
	private boolean					xa;
	@ConfigProperty(defaultValue = "false")
	private Boolean					configDump;
	@ConfigProperty(defaultValue = "false")
	private Boolean					configProfiler;
	@ConfigProperty(defaultValue = "info")
	private String					configConsoleLevel;
	@ConfigProperty(defaultValue = "info")
	private String					configFileLevel;
	@ConfigProperty(defaultValue = "3")
	private Integer					configPoolMinSize;
	@ConfigProperty(defaultValue = "20")
	private Integer					configPoolMaxSize;
	@ConfigProperty(defaultValue = "utf8")
	private String					configEncoding;

	public OrientDBManagedConnectionFactoryImpl() {
		this.logwriter = new PrintWriter(System.out);
	}

	@PostConstruct
	private void initialize() {
		OGlobalConfiguration.ENVIRONMENT_DUMP_CFG_AT_STARTUP.setValue(configDump);
		OGlobalConfiguration.PROFILER_ENABLED.setValue(configProfiler);
		OGlobalConfiguration.LOG_CONSOLE_LEVEL.setValue(configConsoleLevel);
		OGlobalConfiguration.LOG_FILE_LEVEL.setValue(configFileLevel);
		OGlobalConfiguration.NETWORK_HTTP_CONTENT_CHARSET.setValue(configEncoding);
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#start()
	 */
	public void start() {
		databasePool = new OGraphDatabasePool(connectionUrl, username, password);
		databasePool.setup(configPoolMinSize, configPoolMaxSize);
		try {
			databasePool.acquire();
		}
		catch(OStorageException notExist) {
			(new OGraphDatabase(connectionUrl)).create();
			notExist.printStackTrace();
		}
		logger.info("Database pool acquired");
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#stop()
	 */
	public void stop() {
		databasePool.close();
		logger.info("Database pool closed");
	}

	private OGraphDatabase getDatabase() {
		return databasePool.acquire();
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#getTransactionSupport()
	 */
	public TransactionSupportLevel getTransactionSupport() {
		logger.info("Transaction support XA is : " + isXa());
		if(isXa()) {
			return TransactionSupportLevel.XATransaction;
		}else {
			return TransactionSupportLevel.LocalTransaction;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#getResourceAdapter()
	 */
	public ResourceAdapter getResourceAdapter() {
		return ra;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#setResourceAdapter(javax.resource.spi.ResourceAdapter)
	 */
	public void setResourceAdapter(ResourceAdapter ra) throws ResourceException {
		this.ra = (OrientDBResourceAdapter) ra;
		this.ra.addFactory(this);
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#createConnectionFactory()
	 */
	public Object createConnectionFactory() throws ResourceException {
		throw new ResourceException("This resource adapter doesn't support non-managed environments");
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#createConnectionFactory(javax.resource.spi. ConnectionManager)
	 */
	public Object createConnectionFactory(ConnectionManager cxManager) throws ResourceException {
		return new OrientDBConnectionFactoryImpl(this, cxManager);
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#createManagedConnection(javax.security.auth.Subject, javax.resource.spi.ConnectionRequestInfo)
	 */
	public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo cri) throws ResourceException {
		connectionsCreated++;
		return new OrientDBManagedConnectionImpl(this, getDatabase());
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#getLogWriter()
	 */
	public PrintWriter getLogWriter() throws ResourceException {
		logger.info("getLogWriter()");
		return logwriter;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#matchManagedConnections(java.util.Set, javax.security.auth.Subject, javax.resource.spi.ConnectionRequestInfo)
	 */
	@SuppressWarnings("unchecked")
	public ManagedConnection matchManagedConnections(@SuppressWarnings("rawtypes") Set connectionSet, Subject subject, ConnectionRequestInfo cxRequestInfo) throws ResourceException {
		logger.info("matchManagedConnections()");
		ManagedConnection result = null;
		Iterator<ManagedConnection> it = connectionSet.iterator();
		while(result == null && it.hasNext()) {
			ManagedConnection mc = it.next();
			if(mc instanceof OrientDBManagedConnectionImpl) {
				result = mc;
			}

		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#setLogWriter(java.io.PrintWriter)
	 */
	public void setLogWriter(PrintWriter out) throws ResourceException {
		logger.info("setLogWriter()");
		logwriter = out;

	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((ra == null) ? 0 : ra.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#setConnectionUrl(java.lang.String)
	 */
	public void setConnectionUrl(String connectionUrl) {
		this.connectionUrl = connectionUrl;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#isXa()
	 */
	public boolean isXa() {
		return xa;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#setXa(boolean)
	 */
	public void setXa(boolean xa) {
		this.xa = xa;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setConfigConsoleLevel(String configConsoleLevel) {
		this.configConsoleLevel = configConsoleLevel;
	}

	public void setConfigDump(Boolean configDump) {
		this.configDump = configDump;
	}

	public void setConfigEncoding(String configEncoding) {
		this.configEncoding = configEncoding;
	}

	public void setConfigFileLevel(String configFileLevel) {
		this.configFileLevel = configFileLevel;
	}

	public void setConfigPoolMaxSize(Integer configPoolMaxSize) {
		this.configPoolMaxSize = configPoolMaxSize;
	}

	public void setConfigPoolMinSize(Integer configPoolMinSize) {
		this.configPoolMinSize = configPoolMinSize;
	}

	public void setConfigProfiler(Boolean configProfiler) {
		this.configProfiler = configProfiler;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		if(obj instanceof OrientDBManagedConnectionFactory) {
			return true;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.devexpert.orient.jca.OrientDBManagedConnectionFactory#destroyManagedConnection(eu.devexpert.orient.jca.api .OrientDBManagedConnection)
	 */
	public void destroyManagedConnection(OrientDBManagedConnection orientDBManagedConnection) {
		connectionsCreated--;
		if(connectionsCreated <= 0) {
			logger.info("Shutdown database!");
		}
	}

}
