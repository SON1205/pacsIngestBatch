# medIngestEx

병원 PACS 서버에 축적된 **~650TB** 규모의 DICOM 의료영상 데이터를 배치 처리하여 메타데이터를 추출·적재하는 시스템.

## 기술 스택

- Java 25, Spring Boot 4.0.3, Spring Batch 6.x
- PostgreSQL 16, dcm4che 5.34.1
- Gradle 9.3.1

## 실행 방법

```bash
# DB 기동
docker compose up -d

# Job 1: 디렉토리 스캔
./gradlew bootRun --args="--spring.batch.job.name=directoryScanJob"

# Job 2: DICOM 헤더 파싱
./gradlew bootRun --args="--spring.batch.job.name=dicomParseJob"
```

## 배치 파이프라인

1. **directoryScanJob** — PACS 디렉토리 구조를 탐색하여 Study 디렉토리 목록을 DB에 적재
2. **dicomParseJob** — 스캔된 Study 디렉토리에서 대표 DICOM 파일을 선택, 헤더를 파싱하여 메타데이터 적재

## 주요 설계

- **2-Pass 파일 선택**: 경량 메타데이터만 먼저 읽어 대표 파일을 선택한 뒤 전체 헤더 파싱 (메모리 ~4,000배 절감)
- **모달리티 가중치 파티셔닝**: CT/MR 등 무거운 모달리티를 세분화하고 가벼운 모달리티를 빈 패킹하여 스레드 간 부하 균등 분배
- **장애 내성**: Skip 정책 + `ON CONFLICT DO NOTHING` 멱등 쓰기 + ExecutionContext 체크포인트로 중단 지점 재개