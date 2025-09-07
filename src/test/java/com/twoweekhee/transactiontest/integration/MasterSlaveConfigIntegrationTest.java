package com.twoweekhee.transactiontest.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.MySQLContainer;

import com.twoweekhee.transactiontest.User;
import com.twoweekhee.transactiontest.UserService;
import com.twoweekhee.transactiontest.config.TestDockerConfiguration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MasterSlaveConfigIntegrationTest extends BaseIntegrationTest {

	private final UserService userService;

	@BeforeAll
	void setUp() {
		log.info("=== 테스트 환경 초기화 ===");
		log.info("Master Container: {}", TestDockerConfiguration.getmainContainer().getJdbcUrl());
		log.info("Slave Container: {}", TestDockerConfiguration.getreplicaContainer().getJdbcUrl());
	}

	@Test
	@Order(1)
	@DisplayName("Docker 컨테이너 정상 동작 확인")
	void testContainersAreRunning() {
		assertTrue(TestDockerConfiguration.getmainContainer().isRunning());
		assertTrue(TestDockerConfiguration.getreplicaContainer().isRunning());

		log.info("✅ Docker 컨테이너 정상 동작 확인 완료");
	}

	@Test
	@Order(2)
	@DisplayName("Master-Slave 복제 상태 확인")
	void testReplicationStatus() throws Exception {
		MySQLContainer<?> slaveContainer = TestDockerConfiguration.getreplicaContainer();

		// Slave 상태 직접 확인
		try (Connection conn = DriverManager.getConnection(
			slaveContainer.getJdbcUrl(),
			slaveContainer.getUsername(),
			slaveContainer.getPassword())) {

			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SHOW SLAVE STATUS");

			if (rs.next()) {
				String ioRunning = rs.getString("Slave_IO_Running");
				String sqlRunning = rs.getString("Slave_SQL_Running");

				log.info("Slave_IO_Running: {}", ioRunning);
				log.info("Slave_SQL_Running: {}", sqlRunning);

				// 복제가 정상적으로 설정되어 있는지 확인
				assertEquals("Yes", sqlRunning);
			}
		}

		log.info("✅ Master-Slave 복제 상태 확인 완료");
	}

	@Test
	@Order(3)
	@DisplayName("Write 트랜잭션 → Master DB 라우팅 테스트")
	void testWriteTransactionToMaster() {
		// When
		log.info("=== Write 트랜잭션 테스트 시작 ===");
		User user1 = userService.createUser("Master Test User", "master@test.com");
		User user2 = userService.createUser("Slave Test User", "slave@test.com");

		// Then
		assertNotNull(user1);
		assertNotNull(user1.getId());
		assertEquals("Master Test User", user1.getName());

		assertNotNull(user2);
		assertNotNull(user2.getId());
		assertEquals("Slave Test User", user2.getName());

		log.info("✅ Write 트랜잭션 → Master DB 라우팅 완료");
	}

	@Test
	@Order(4)
	@DisplayName("Read-Only 트랜잭션 → Slave DB 라우팅 테스트")
	void testReadOnlyTransactionToSlave() {

		// When
		log.info("=== Read-Only 트랜잭션 테스트 시작 ===");
		List<User> users = userService.findAllUsersReadOnly("Slave Test User");

		// Then
		assertEquals(1, users.size());
		assertEquals("Slave Test User", users.get(0).getName());
		log.info("✅ Read-Only 트랜잭션 → Slave DB 라우팅 완료");
	}
}

