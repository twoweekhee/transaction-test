package com.twoweekhee.transactiontest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserOuterService {

	private final UserService userService;

	public User testReplicaToMain(String name, String email) {
		log.info("=== Read-Only 트랜잭션 테스트 시작 ===");
		userService.findAllUsersReadOnly(name);
		log.info("=== Write 트랜잭션 테스트 시작 ===");
		return userService.createUser(name, email);
	}

	@Transactional
	public User testReplicaToMainWithTransaction(String name, String email) {
		log.info("=== Read-Only 트랜잭션 테스트 시작 ===");
		userService.findAllUsersReadOnly(name);
		log.info("=== Write 트랜잭션 테스트 시작 ===");
		return userService.createUser(name, email);
	}

	@Transactional
	public User testReplicaToMainWithNew(String name, String email) {
		log.info("=== Read-Only 트랜잭션 테스트 시작 ===");
		userService.findAllUsersReadOnly(name);
		log.info("=== Write 트랜잭션 테스트 시작 ===");
		return userService.createUserNew(name, email);
	}
}
