package com.twoweekhee.transactiontest;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	@Transactional
	public User createUser(String name, String email) {
		log.info("=== createUserRequiresNew 시작 ===");
		log.info("트랜잭션 이름: {}", TransactionSynchronizationManager.getCurrentTransactionName());
		log.info("트랜잭션 ReadOnly: {}", TransactionSynchronizationManager.isCurrentTransactionReadOnly());
		log.info("트랜잭션 Active: {}", TransactionSynchronizationManager.isActualTransactionActive());


		return userRepository.save(User.builder()
			.name(name)
			.email(email)
			.build());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public User createUserNew(String name, String email) {
		log.info("=== createUserRequiresNew 시작 ===");
		log.info("트랜잭션 이름: {}", TransactionSynchronizationManager.getCurrentTransactionName());
		log.info("트랜잭션 ReadOnly: {}", TransactionSynchronizationManager.isCurrentTransactionReadOnly());
		log.info("트랜잭션 Active: {}", TransactionSynchronizationManager.isActualTransactionActive());

		return userRepository.save(User.builder()
			.name(name)
			.email(email)
			.build());
	}

	@Transactional(readOnly = true)
	public List<User> findAllUsersReadOnly(String name) {
		log.info("=== createUserRequiresNew 시작 ===");
		log.info("트랜잭션 이름: {}", TransactionSynchronizationManager.getCurrentTransactionName());
		log.info("트랜잭션 ReadOnly: {}", TransactionSynchronizationManager.isCurrentTransactionReadOnly());
		log.info("트랜잭션 Active: {}", TransactionSynchronizationManager.isActualTransactionActive());

		return userRepository.findAllByName(name);
	}
}
