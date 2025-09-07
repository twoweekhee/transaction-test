# Transaction Test Project

## 1. 프로젝트 개요

본 프로젝트는 Spring Boot 환경에서 Master-Slave 데이터베이스 구조를 구축하고, 트랜잭션의 Read-Only 속성에 따라 Master와 Slave 데이터베이스로 요청을 분기(Routing)하는 기능을 테스트하기 위해 제작되었습니다.

주요 테스트 시나리오는 다음과 같습니다.

*   **쓰기 트랜잭션**: Master 데이터베이스로 정상적으로 라우팅 되는지 확인합니다.
*   **읽기 트랜잭션**: Slave 데이터베이스로 정상적으로 라우팅 되는지 확인합니다.
*   **트랜잭션 전파**: 하나의 트랜잭션 내에서 읽기(Slave)와 쓰기(Master) 작업이 함께 호출될 경우, 트랜잭션이 어떻게 전파되고 어떤 데이터베이스로 라우팅 되는지 테스트합니다.

이를 통해 데이터베이스의 부하를 분산하고, 데이터 정합성을 유지하는 방법을 검증합니다.

## 2. 사용 기술

*   **언어**: Java 21
*   **프레임워크**: Spring Boot 3.x, Spring Data JPA
*   **데이터베이스**: MySQL 8.0
*   **테스트**: JUnit 5, Testcontainers
*   **라이브러리**: Lombok

## 3. 실행 방법

### 3.1. 환경 설정

본 프로젝트는 Docker와 Testcontainers를 사용하여 테스트 환경을 자동으로 구축하므로, 별도의 MySQL 설치나 설정이 필요 없습니다.

*   **Docker Desktop**: 로컬 환경에 Docker가 설치되어 있어야 합니다.

### 3.2. 테스트 실행

프로젝트의 모든 테스트는 다음 명령어를 통해 실행할 수 있습니다.

```bash
./gradlew test
```

테스트가 실행되면 Testcontainers가 자동으로 MySQL Master-Slave 환경을 구성하고 테스트를 진행합니다.

## 4. 트랜잭션 테스트 상세

### 4.1. Master-Slave 라우팅 테스트 (`MasterSlaveConfigIntegrationTest.java`)

`TestReplicationRoutingDataSource`는 트랜잭션의 `readOnly` 속성을 감지하여 Master 또는 Slave 데이터베이스로 라우팅합니다.

*   `@Transactional(readOnly = false)` 또는 `@Transactional` (기본값): **Master** 데이터베이스로 라우팅
*   `@Transactional(readOnly = true)`: **Slave** 데이터베이스로 라우팅

#### 테스트 시나리오 및 결과

1.  **Docker 컨테이너 정상 동작 확인**: `mainContainer`와 `replicaContainer`가 정상적으로 실행되는지 확인합니다.
2.  **Master-Slave 복제 상태 확인**: Slave DB의 복제 상태(`Slave_IO_Running`, `Slave_SQL_Running`)가 `Yes`인지 확인하여 복제가 정상적으로 이루어졌는지 검증합니다.
3.  **Write 트랜잭션 → Master DB 라우팅 테스트**:
    *   `userService.createUser()`를 호출하여 새로운 사용자를 생성합니다.
    *   해당 트랜잭션은 `readOnly = false`이므로 Master 데이터베이스로 라우팅되어야 합니다.
    *   테스트 결과, 사용자가 정상적으로 생성되고 ID가 발급되는 것을 확인합니다.
4.  **Read-Only 트랜잭션 → Slave DB 라우팅 테스트**:
    *   `userService.findAllUsersReadOnly()`를 호출하여 사용자를 조회합니다.
    *   해당 트랜잭션은 `readOnly = true`이므로 Slave 데이터베이스로 라우팅되어야 합니다.
    *   Master에서 생성한 사용자가 Slave로 정상적으로 복제되었는지 확인하고, 조회 결과를 검증합니다.

### 4.2. 트랜잭션 전파 테스트 (`SlaveToMasterTransactionConfigIntegrationTest.java`)

하나의 서비스 메소드 내에서 읽기(Slave)와 쓰기(Master) 작업이 연속적으로 호출될 때의 트랜잭션 동작을 테스트합니다.

#### 테스트 시나리오 및 결과

1.  **트랜잭션 없이 Read-Only → Master**:
    *   `userOuterService.testReplicaToMain()` 호출
    *   외부 트랜잭션이 없으므로, 내부의 `findAllUsersReadOnly()`는 `readOnly=true` 트랜잭션(Slave)으로, `createUser()`는 `readOnly=false` 트랜잭션(Master)으로 각각 독립적으로 실행됩니다.
    *   **결과**: 정상 동작합니다.
2.  **트랜잭션 내에 Read-Only → Master**:
    *   `userOuterService.testReplicaToMainWithTransaction()` 호출
    *   외부 트랜잭션(`@Transactional`)이 `readOnly=false`이므로, 내부에 포함된 `findAllUsersReadOnly()` 메소드 또한 **외부 트랜잭션의 속성을 따라 `readOnly=false`로 동작**합니다. 따라서 읽기 작업도 Master 데이터베이스로 라우팅됩니다.
    *   **결과**: 모든 작업이 Master DB에서 처리되어 정상 동작합니다.
3.  **트랜잭션 내에 Read-Only → Master (`REQUIRES_NEW`)**:
    *   `userOuterService.testReplicaToMainWithNew()` 호출
    *   외부 트랜잭션(`@Transactional`)이 `readOnly=false`일 때, `createUserNew()` 메소드는 `propagation = Propagation.REQUIRES_NEW` 속성으로 인해 **새로운 트랜잭션**을 시작합니다.
    *   `findAllUsersReadOnly()`는 외부 트랜잭션(Master)에 참여하고, `createUserNew()`는 새로운 `readOnly=false` 트랜잭션(Master)을 생성하여 실행됩니다.
    *   **결과**: 모든 작업이 Master DB에서 처리되어 정상 동작합니다.

## 5. 결론

본 프로젝트를 통해 Master-Slave 환경에서 트랜잭션의 `readOnly` 속성에 따라 데이터베이스 연결이 동적으로 라우팅되는 것을 확인했습니다. 또한, 트랜잭션 전파 속성에 따라 내부 트랜잭션의 동작이 어떻게 달라지는지 검증할 수 있었습니다.

이를 통해 읽기 작업은 Slave로 분산하여 Master의 부하를 줄이고, 쓰기 작업은 Master에서 처리하여 데이터 정합성을 보장하는 전략을 효과적으로 구현할 수 있습니다.
