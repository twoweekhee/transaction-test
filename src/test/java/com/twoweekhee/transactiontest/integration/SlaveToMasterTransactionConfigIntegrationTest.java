package com.twoweekhee.transactiontest.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

import com.twoweekhee.transactiontest.User;
import com.twoweekhee.transactiontest.UserOuterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SlaveToMasterTransactionConfigIntegrationTest extends BaseIntegrationTest {

	private final UserOuterService userService;

	@Test
	@DisplayName("트랜잭션 없이 Read-Only-> Master")
	void testReplicaToMain() {

		// When
		User user = userService.testReplicaToMain("test", "test@gmail.com");

		// Then
		assertEquals("test", user.getName());
	}

	@Test
	@DisplayName("트랜잭션 내에 Read-Only-> Master")
	void testReplicaToMainWithTransaction() {

		// 외부 트랜잭션이 read only가 아니면 내부 전파에 read only가 있어도 read only false
		// When
		User user = userService.testReplicaToMainWithTransaction("test", "test@gmail.com");

		// Then
		assertEquals("test", user.getName());
	}

	@Test
	@DisplayName("트랜잭션 내에 Read-Only-> Master(requires_new)")
	void testReplicaToMainWithNew() {

		// When
		User user = userService.testReplicaToMainWithNew("test", "test@gmail.com");

		// Then
		assertEquals("test", user.getName());
	}
}

