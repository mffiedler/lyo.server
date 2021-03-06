/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *  
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  
 *  Contributors:
 *  
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.lyo.server.oauth.consumerstore;

import java.sql.SQLException;

import org.apache.log4j.Logger;
import org.eclipse.lyo.server.oauth.core.consumer.AbstractConsumerStore;
import org.eclipse.lyo.server.oauth.core.consumer.ConsumerStoreException;
import org.eclipse.lyo.server.oauth.core.consumer.LyoOAuthConsumer;

import com.hp.hpl.jena.db.DBConnection;
import com.hp.hpl.jena.db.IDBConnection;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.ModelMaker;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.shared.Command;
import com.hp.hpl.jena.shared.JenaException;
import com.hp.hpl.jena.shared.Lock;
import com.hp.hpl.jena.shared.PropertyNotFoundException;
import com.hp.hpl.jena.vocabulary.RDF;

/**
 * A simple RDF consumer store backed by an embedded Derby database.
 * 
 * @author Samuel Padgett <spadgett@us.ibm.com>
 */
public class RdfConsumerStore extends AbstractConsumerStore {
	protected final static String LYO_OAUTH_NAMESPACE = "http://eclipse.org/lyo/server/oauth#";
	protected final static String CONSUMER_RESOURCE = LYO_OAUTH_NAMESPACE
			+ "Consumer";
	protected final static String CALLBACK_URL = LYO_OAUTH_NAMESPACE
			+ "callback";
	protected final static String CONSUMER_NAME = LYO_OAUTH_NAMESPACE
			+ "consumerName";
	protected final static String CONSUMER_KEY = LYO_OAUTH_NAMESPACE
			+ "consumerKey";
	protected final static String CONSUMER_SECRET = LYO_OAUTH_NAMESPACE
			+ "consumerSecret";
	protected final static String PROVISIONAL = LYO_OAUTH_NAMESPACE
			+ "provisional";
	protected final static String TRUSTED = LYO_OAUTH_NAMESPACE + "trusted";

	protected final static String DB_URL = "jdbc:derby:consumerStore;create=true"; // URL of database
	protected final static String DB_USER = ""; // database user id
	protected final static String DB_PASSWD = ""; // database password
	protected final static String DB = "Derby"; // database type

	private Logger logger = Logger.getLogger(RdfConsumerStore.class);
	
	private Model model;

	public RdfConsumerStore() throws SQLException, ConsumerStoreException,
			ClassNotFoundException {
		createModel();
		loadConsumers();
	}

	public RdfConsumerStore(Model model) throws ConsumerStoreException {
		this.model = model;
		loadConsumers();
	}

	protected void createModel() {
		try {
			Class.forName("org.apache.derby.jdbc.EmbeddedDriver").newInstance();

			IDBConnection conn = new DBConnection(DB_URL, DB_USER, DB_PASSWD, DB);
			ModelMaker maker = ModelFactory.createModelRDBMaker(conn);
			model = maker.createDefaultModel();
			conn.close();
		} catch (Exception e) {
			logger.error("Could not create OAuth consumer store.", e);
		}
	}

	protected void loadConsumers() throws ConsumerStoreException {
		try {
			model.enterCriticalSection(Lock.READ);
			ResIterator i = model.listResourcesWithProperty(RDF.type,
					model.createResource(CONSUMER_RESOURCE));
			while (i.hasNext()) {
				Resource consumerResource = i.next();
				try {
					add(fromResource(consumerResource));
				} catch (PropertyNotFoundException e) {
					// The resource is missing some properties.
					// Not good, but other consumer resources might
					// be OK, so continue. (Log the error, though.)
					logger.error(
							"Could not load consumer "
									+ consumerResource.getProperty(model
											.createProperty(CONSUMER_NAME))
									+ " ("
									+ consumerResource.getProperty(model
											.createProperty(CONSUMER_KEY))
									+ ")", e);
				} catch (JenaException e) {
					// Some other runtime exception occurred.
					throw new ConsumerStoreException(e);
				}
			}
		} finally {
			model.leaveCriticalSection();
		}
	}

	@Override
	public LyoOAuthConsumer addConsumer(final LyoOAuthConsumer consumer)
			throws ConsumerStoreException {
		if (model == null) {
			throw new ConsumerStoreException("Consumer store not initialized.");
		}
		
		try {
			model.enterCriticalSection(Lock.WRITE);
			model.executeInTransaction(new Command() {
				@Override
				public Object execute() {
					removeProperties(consumer);
					toResource(consumer);
					
					return consumer;
				}
			});
						
			return add(consumer);
		} catch (JenaException e) {
			throw new ConsumerStoreException(e);
		} finally {
			model.leaveCriticalSection();
		}
	}

	@Override
	public LyoOAuthConsumer removeConsumer(final String consumerKey)
			throws ConsumerStoreException {
		if (model == null) {
			throw new ConsumerStoreException("Consumer store not initialized.");
		}
		
		try {
			model.enterCriticalSection(Lock.WRITE);
			model.executeInTransaction(new Command() {
				@Override
				public Object execute() {
					removeProperties(consumerKey);
					
					return consumerKey;
				}
			});
	
			return remove(consumerKey);
		} catch (JenaException e) {
			throw new ConsumerStoreException(e);
		} finally {
			model.leaveCriticalSection();
		}
	}

	@Override
	public LyoOAuthConsumer updateConsumer(LyoOAuthConsumer consumer)
			throws ConsumerStoreException {
		// addConsumer() also works for update.
		return addConsumer(consumer);
	}

	/**
	 * Removes any properties previously associated with the consumer.
	 * 
	 * @param consumerKey
	 *            the consumer key
	 */
	protected void removeProperties(String consumerKey) {
		ResIterator i = model.listResourcesWithProperty(
				model.createProperty(CONSUMER_KEY),
				model.createLiteral(consumerKey));
		while (i.hasNext()) {
			i.next().removeProperties();
		}
	}
	
	/**
	 * Removes any properties previously associated with the consumer.
	 * 
	 * @param consumer the consumer
	 */
	protected void removeProperties(LyoOAuthConsumer consumer) {
		removeProperties(consumer.consumerKey);
	}

	protected Resource toResource(LyoOAuthConsumer consumer) {
		Resource resource = model.createResource();
		resource.addProperty(RDF.type, model.createResource(CONSUMER_RESOURCE));
		resource.addProperty(model.createProperty(CONSUMER_NAME),
				consumer.getName());
		resource.addProperty(model.createProperty(CONSUMER_KEY),
				consumer.consumerKey);
		resource.addProperty(model.createProperty(CONSUMER_SECRET),
				consumer.consumerSecret);
		resource.addProperty(model.createProperty(PROVISIONAL),
				(consumer.isProvisional()) ? "true" : "false");
		resource.addProperty(model.createProperty(TRUSTED),
				(consumer.isTrusted()) ? "true" : "false");

		return resource;
	}

	protected LyoOAuthConsumer fromResource(Resource resource) {
		String key = resource.getRequiredProperty(
				model.createProperty(CONSUMER_KEY)).getString();
		String secret = resource.getRequiredProperty(
				model.createProperty(CONSUMER_SECRET)).getString();
		LyoOAuthConsumer consumer = new LyoOAuthConsumer(key, secret);
		consumer.setName(resource.getRequiredProperty(
				model.createProperty(CONSUMER_NAME)).getString());

		String provisional = resource.getProperty(
				model.createProperty(PROVISIONAL)).getString();
		consumer.setProvisional("true".equals(provisional));

		String trusted = resource.getProperty(model.createProperty(TRUSTED))
				.getString();
		consumer.setTrusted("true".equals(trusted));

		return consumer;
	}
}
