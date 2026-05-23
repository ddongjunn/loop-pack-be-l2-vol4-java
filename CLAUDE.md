# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 개요

Loopers 커머스 백엔드 — 멀티 모듈 Spring Boot 프로젝트(Loopers Java/Spring 템플릿). 그룹은 `com.loopers`, 루트 프로젝트는 `loopers-java-spring-template`. `commerce-api`의 `example` 패키지는 계층 구조를 보여주는 레퍼런스 구현이며, `product`는 동일 패턴으로 만든 첫 실제 도메인이다.

## 기술 스택

- **Java 21** (Gradle toolchain), **Gradle 8.13** (Kotlin DSL, wrapper 커밋됨)
- **Spring Boot 3.4.4**, Spring Dependency Management 1.1.7, **Spring Cloud 2024.0.1**
- **JPA/Hibernate** + **QueryDSL** (jakarta classifier), **MySQL 8**
- **Spring Kafka**, **Spring Data Redis**, **Spring Batch**
- **Lombok**, springdoc-openapi 2.7.0 (Swagger UI)
- 테스트: JUnit 5, Testcontainers (mysql/redis/kafka), springmockk 4.0.2, mockito-core 5.14.0, instancio-junit 5.0.2
- 모니터링: Micrometer + Prometheus, Brave 트레이싱, logback Slack appender

버전은 `gradle.properties`에 중앙 관리된다. 버전 고정/변경은 빌드 스크립트가 아니라 이 파일에서 한다.

## 모듈 구조

```text
apps/        실행 가능한 @SpringBootApplication 모듈 — bootJar는 여기서만 생성
  commerce-api       REST API (web + actuator + swagger)
  commerce-batch     Spring Batch 잡
  commerce-streamer  Kafka 컨슈머
modules/     도메인 비의존 재사용 설정 (java-library + java-test-fixtures)
  jpa        DataSource/JPA/QueryDSL 설정, BaseEntity, MySQL Testcontainers 픽스처
  redis      master-replica Redis 설정, Redis Testcontainers 픽스처
  kafka      Kafka producer/consumer + batch-listener 설정, Kafka Testcontainers 픽스처
supports/    부가 기능 add-on (logging, monitoring, jackson)
```

`apps`는 `modules` + `supports`에 의존한다. `modules`/`supports`는 절대 `apps`나 특정 도메인에 의존하지 않는다. apps의 테스트 코드는 `testImplementation(testFixtures(project(":modules:...")))`로 모듈 테스트 픽스처를 사용한다.

## 빌드 & 테스트

```bash
./gradlew build                       # 전체 모듈 빌드 + 테스트
./gradlew build -x test               # 테스트 제외 빌드
./gradlew :apps:commerce-api:build    # 단일 모듈 빌드

./gradlew test                        # 전체 테스트
./gradlew :apps:commerce-api:test     # 특정 모듈 테스트
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.example.ExampleModelTest"   # 단일 테스트 클래스
./gradlew :apps:commerce-api:test --tests "*.ExampleModelTest.someMethod"                  # 단일 테스트 메서드

./gradlew jacocoTestReport            # 커버리지 XML 리포트 (test 이후 실행)
```

앱 실행 (먼저 아래의 로컬 인프라를 띄울 것):
```bash
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun --args='--job.name=demoJob'   # 배치 잡은 이름으로 opt-in
```

루트 `build.gradle.kts`가 강제하는 빌드 규칙:
- `bootJar`는 `apps/*`에만 활성화된다. `modules`/`supports`는 일반 jar를 생성한다.
- 컨테이너 프로젝트 `apps`, `modules`, `supports`는 모든 task가 비활성화돼 있다 — 이 프로젝트에 직접 task를 실행하지 말 것.
- 테스트는 단일 스레드(`maxParallelForks = 1`)로, `Asia/Seoul` 타임존과 `test` 프로파일에서 실행된다.
- 프로젝트 `version`은 명시적으로 지정하지 않으면 git short hash가 된다.

## 로컬 인프라

```bash
docker-compose -f ./docker/infra-compose.yml up        # MySQL 8, Redis master+replica, Kafka(KRaft), kafka-ui:9099
docker-compose -f ./docker/monitoring-compose.yml up   # Prometheus + Grafana (http://localhost:3000, admin/admin)
```

`local`/`test` 프로파일은 이 인프라(localhost)를 바라본다. 그 외 프로파일(`dev`/`qa`/`prd`)은 환경변수가 필요하다: `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_USER`/`MYSQL_PWD`, `REDIS_MASTER_HOST`/`REDIS_MASTER_PORT`, `REDIS_REPLICA_1_HOST`/`REDIS_REPLICA_1_PORT`, `BOOTSTRAP_SERVERS`. 테스트는 Testcontainers를 쓰므로 Docker 데몬은 필요하지만 compose 스택은 필요 없다.

## 아키텍처 (commerce-api)

도메인당 하나의 패키지(`com.loopers.<layer>.<domain>`)를 갖는 클린/레이어드 아키텍처. 의존성은 안쪽을 향한다: `interfaces → application → domain ← infrastructure`.

| 계층 | 패키지 | 역할 |
|------|--------|------|
| Interface | `interfaces/api/<domain>` | `XxxV1Controller`(REST), `XxxV1Dto`(요청/응답 record), `XxxV1ApiSpec`(Swagger 문서용 인터페이스 — 컨트롤러가 `implements`) |
| Application | `application/<domain>` | `XxxFacade`(`@Component`, 유스케이스 조합), `XxxInfo`(출력 DTO, `from(model)`) |
| Domain | `domain/<domain>` | `XxxModel`(`@Entity`, 생성자/메서드에서 자신의 불변식 검증), `XxxService`(`@Component`, `@Transactional` 비즈니스 로직), `XxxRepository`(포트 인터페이스) |
| Infrastructure | `infrastructure/<domain>` | `XxxRepositoryImpl`(`@Component`, 포트 구현), `XxxJpaRepository`(Spring Data 인터페이스) |

호출 흐름: Controller가 `V1Dto` → Facade 인자로 매핑 → Service가 도메인 `Model`을 반환 → Facade가 `Info`로 래핑 → Controller가 `ApiResponse`로 래핑. Repository **포트**는 `domain`에, JPA **어댑터**는 `infrastructure`에 있다 — 도메인 코드는 Spring Data를 import하지 않는다. JPA 리포지토리 스캔 범위는 `com.loopers.infrastructure`로 한정돼 있다(`JpaConfig`).

도메인을 새로 추가할 때는 이 파일 세트를 그대로 복제하고 기존 네이밍을 정확히 따른다. `XxxV1ApiSpec`은 `example` 도메인이 따르는 레퍼런스 패턴이며, `product`에는 아직 적용돼 있지 않다 — 새 도메인에는 적용을 권장한다.

### 공통 규약

- **엔티티**는 `BaseEntity`(`modules/jpa`)를 상속한다: IDENTITY id, `createdAt`/`updatedAt`/`deletedAt`, 멱등 soft-delete `delete()`/`restore()`, 그리고 `@PrePersist`/`@PreUpdate` 시점에 호출되는 검증 훅 `guard()`.
- **에러**: `CoreException(ErrorType, "<한국어 메시지>")`를 던진다. `ErrorType` enum이 HTTP 상태 + 코드로 매핑된다(`BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`). 도메인 검증 실패는 `Model`/`Service`에서 던지는 `CoreException`이다.
- **응답**: 모든 엔드포인트는 `ApiResponse<T>`(`meta` + `data`)를 반환한다. `ApiControllerAdvice`(`@RestControllerAdvice`)가 바인딩/Jackson 오류를 포함한 모든 예외를 한국어 사용자 메시지를 담은 `ApiResponse.fail`로 변환한다. 사용자 노출 메시지는 한국어로 작성한다.
- **설정**: 각 앱의 `application.yml`이 `spring.config.import`로 공유 모듈 설정(`jpa.yml`, `redis.yml`, `kafka.yml`, `logging.yml`, `monitoring.yml`)을 가져온다. 프로파일: `local`(기본), `test`, `dev`, `qa`, `prd`.

### 인프라 모듈 세부사항

- **JPA**(`jpa.yml`): 표준 `spring.datasource`가 아닌 커스텀 네임스페이스 `datasource.mysql-jpa.main`(HikariCP)을 사용하며 `DataSourceConfig`가 수동으로 와이어링한다. `ddl-auto`는 `local`/`test`에서 `create`, 그 외에는 `none`. `open-in-view: false`, UTC 저장, `default_batch_fetch_size: 100`.
- **Redis**(`redis.yml`): 커스텀 네임스페이스 `datasource.redis` 아래 master + read-replica 구성, `RedisConfig`/`RedisProperties`가 와이어링한다.
- **Kafka**(`KafkaConfig`): 배치 리스너 컨테이너 팩토리 빈 `KafkaConfig.BATCH_LISTENER`를 노출한다 — concurrency 3, manual ack, `max.poll.records=3000`. `@KafkaListener`는 `containerFactory = KafkaConfig.BATCH_LISTENER`로 이를 참조한다.
- **배치**: 모든 잡은 `@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = "<jobName>")`로 게이팅돼, 실행 시 `--job.name`이 지정한 잡만 로딩된다.

## 개발 워크플로우 — TDD

기능 구현은 Red → Green → Refactor 사이클을 따른다.

- **Red**: 실패하는 테스트를 먼저 작성한다.
- **Green**: Red 단계의 테스트가 모두 통과하는 코드를 작성한다. 오버엔지니어링 금지.
- **Refactor**: 불필요한 private 함수를 지양하고 객체지향적으로 작성한다. unused import를 제거하고 성능을 최적화한다. 모든 테스트 케이스가 통과해야 한다.

테스트 코드는 Arrange → Act → Assert 구조로 작성한다.

- **Arrange**: 테스트 데이터와 조건 준비
- **Act**: 테스트 대상 실행
- **Assert**: 기대 결과 검증

## 주의사항

### 1. 절대 하지 말 것 (Never Do)

- 실제 동작하지 않는 코드, 불필요한 Mock 데이터에 기댄 구현
- null-safety 하지 않은 코드 작성 (Java에서는 `Optional`을 활용한다)
- `println` 등 디버그 출력 코드를 남기는 것

### 2. 권장 (Recommendation)

- 실제 API를 호출해 확인하는 E2E 테스트 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안 제시
- 개발 완료된 API는 `http/<app>/<name>.http` 에 분류해 작성 (예: `http/commerce-api/example-v1.http`)

### 3. 우선순위 (Priority)

1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지
