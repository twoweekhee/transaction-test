package com.twoweekhee.transactiontest.config;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;

import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
public class TestDockerConfiguration {

	private static final Network network = Network.newNetwork();

	private static final MySQLContainer<?> mainContainer = new MySQLContainer<>("mysql:8.0")
		.withNetworkAliases("mysql-main")
		.withNetwork(network)
		.withDatabaseName("test")
		.withUsername("root")
		.withPassword("root1234")
		.withCommand(
			"--server-id=1",
			"--log-bin=mysql-bin",
			"--binlog-format=ROW",
			"--binlog-do-db=test"
		)
		.withReuse(true);

	private static final MySQLContainer<?> replicaContainer = new MySQLContainer<>("mysql:8.0")
		.withNetworkAliases("mysql-replica")
		.withNetwork(network)
		.withDatabaseName("test")
		.withUsername("root")
		.withPassword("root1234")
		.withCommand(
			"--server-id=2",
			"--relay-log=mysql-relay-bin",
			"--log-bin=mysql-bin",
			"--binlog-format=ROW",
			"--replicate-do-db=test",
			"--read-only=1"
		)
		.withReuse(true);

	static {
		// 컨테이너 시작
		Startables.deepStart(Stream.of(mainContainer, replicaContainer)).join();

		// 복제 설정
		setupReplication();

		log.info("=== Docker MySQL Master-Slave 컨테이너 준비 완료 ===");
		log.info("Master URL: {}", mainContainer.getJdbcUrl());
		log.info("Slave URL: {}", replicaContainer.getJdbcUrl());
	}

	@Bean
	public DataSource maintDataSource() {
		HikariDataSource dataSource = new HikariDataSource();
		dataSource.setJdbcUrl(mainContainer.getJdbcUrl());
		dataSource.setUsername(mainContainer.getUsername());
		dataSource.setPassword(mainContainer.getPassword());
		dataSource.setDriverClassName(mainContainer.getDriverClassName());
		dataSource.setPoolName("TestMasterPool");
		dataSource.setMaximumPoolSize(10);
		dataSource.setMinimumIdle(2);
		return dataSource;
	}

	@Bean
	public DataSource replicaDataSource() {
		HikariDataSource dataSource = new HikariDataSource();
		dataSource.setJdbcUrl(replicaContainer.getJdbcUrl());
		dataSource.setUsername(replicaContainer.getUsername());
		dataSource.setPassword(replicaContainer.getPassword());
		dataSource.setDriverClassName(replicaContainer.getDriverClassName());
		dataSource.setPoolName("TestSlavePool");
		dataSource.setMaximumPoolSize(10);
		dataSource.setMinimumIdle(2);
		return dataSource;
	}

	@Bean
	public DataSource routingDataSource() {
		TestReplicationRoutingDataSource routingDataSource = new TestReplicationRoutingDataSource();

		Map<Object, Object> dataSourceMap = new HashMap<>();
		dataSourceMap.put("main", maintDataSource());
		dataSourceMap.put("replica", replicaDataSource());

		routingDataSource.setTargetDataSources(dataSourceMap);
		routingDataSource.setDefaultTargetDataSource(maintDataSource());
		routingDataSource.afterPropertiesSet();

		return routingDataSource;
	}

	@Bean
	@Primary
	public DataSource dataSource() {
		return new LazyConnectionDataSourceProxy(routingDataSource());
	}

	private static void setupReplication() {
		try {
			log.info("MySQL 복제 설정 시작...");

			// Master에서 복제 사용자 생성 + REPLICATION CLIENT 권한 추가
			mainContainer.execInContainer("mysql", "-u", "root", "-p" + mainContainer.getPassword(),
				"-e", "CREATE USER IF NOT EXISTS 'replicator'@'%' IDENTIFIED WITH mysql_native_password BY 'replicator_password';" +
					"GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'replicator'@'%';" +  // REPLICATION CLIENT 추가
					"FLUSH PRIVILEGES;");

			// Slave에서도 동일한 사용자와 권한 생성
			replicaContainer.execInContainer("mysql", "-u", "root", "-p" + replicaContainer.getPassword(),
				"-e", "CREATE USER IF NOT EXISTS 'replicator'@'%' IDENTIFIED WITH mysql_native_password BY 'replicator_password';" +
					"GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'replicator'@'%';" +  // REPLICATION CLIENT 추가
					"FLUSH PRIVILEGES;");

			// Master 상태 확인
			var masterStatus = mainContainer.execInContainer("mysql", "-u", "root", "-p" + mainContainer.getPassword(),
				"-e", "SHOW MASTER STATUS\\G");

			log.info("Master Status: {}", masterStatus.getStdout());

			String masterHost = mainContainer.getHost();
			Integer masterPort = mainContainer.getMappedPort(3306);

			// 복제 설정
			replicaContainer.execInContainer("mysql", "-u", "root", "-p" + replicaContainer.getPassword(),
				"-e", "CHANGE MASTER TO " +
					"MASTER_HOST='" + masterHost + "', " +
					"MASTER_PORT=" + masterPort + ", " +
					"MASTER_USER='replicator', " +
					"MASTER_PASSWORD='replicator_password', " +
					"MASTER_AUTO_POSITION=1; " +
					"START SLAVE;");

			log.info("MySQL 복제 설정 완료");

		} catch (Exception e) {
			log.error("복제 설정 실패", e);
		}
	}

	public static MySQLContainer<?> getmainContainer() {
		return mainContainer;
	}

	public static MySQLContainer<?> getreplicaContainer() {
		return replicaContainer;
	}
}
