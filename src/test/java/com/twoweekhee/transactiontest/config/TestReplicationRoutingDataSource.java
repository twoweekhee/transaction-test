package com.twoweekhee.transactiontest.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestReplicationRoutingDataSource extends AbstractRoutingDataSource {

	@Override
	protected Object determineCurrentLookupKey() {
		boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
		String dataSourceKey = isReadOnly ? "slave" : "master";

		log.info("=== Test DataSource Routing ===");
		log.info("Transaction ReadOnly: {}", isReadOnly);
		log.info("Selected DataSource: {}", dataSourceKey);
		log.info("Transaction Active: {}", TransactionSynchronizationManager.isActualTransactionActive());
		log.info("Transaction Name: {}", TransactionSynchronizationManager.getCurrentTransactionName());
		log.info("================================");

		return dataSourceKey;
	}
}
