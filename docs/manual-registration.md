# 교범 등록 API

`POST /api/v1/manuals` — JWT 인증 관리자(`ROLE_ADMIN`)만 호출할 수 있으며 성공 시 `201 Created`를 반환한다.

Controller의 `@AuthenticationPrincipal(expression = "userAuthDto.userId") Long userId`는 Spring Security 기본 기능으로 JWT 필터가 검증한 인증 정보에서 주입한다.
사용자 ID를 요청 파라미터로 전달하지 않는다. `@NeedAdminRole` AOP가 Controller 실행 전에 관리자 여부를 검증한다.
권한은 JWT 인증 과정에서 조회한 현재 사용자 정보를 기준으로 하므로 토큰 발급 후 권한을 회수하면 접근이 차단된다.

## 요청

`multipart/form-data`의 필수 파트:

| 파트 | Content-Type | 내용 |
| --- | --- | --- |
| `request` | `application/json` | 교범 정보 JSON |
| `file` | 파일의 MIME 또는 `application/octet-stream` | 교범 원본 파일 1개 |

```json
{
  "manualTitle": "분대 전술 교범",
  "trainingFieldId": 1,
  "manualDescription": "분대 전술 훈련 참고 자료"
}
```

- 제목: 공백만 입력 불가, 최대 100자. 저장 시 앞뒤 공백을 제거한다.
- 훈련 분야 ID: 양의 정수이며 실제로 존재하는 활성 분야여야 한다.
- 설명: 선택 입력, 최대 1000자.
- 파일: PDF, PPT, PPTX, DOC, DOCX, HWP 5. 실제 파일 내용과 확장자가 일치해야 한다.
- 기본 용량 제한: 파일 50MB, 전체 요청 55MB. 빈 파일과 경로 문자가 포함된 파일명은 거부한다.
- 분석은 형식과 메타데이터 판별이다. 본문 추출·OCR·전체 문서 무결성 검증은 수행하지 않는다.

프런트엔드 전송 예시:

```javascript
const form = new FormData();
form.append("request", new Blob([JSON.stringify({
  manualTitle,
  trainingFieldId: Number(trainingFieldId),
  manualDescription
})], { type: "application/json" }));
form.append("file", selectedFile);

const response = await fetch(`${apiBaseUrl}/api/v1/manuals`, {
  method: "POST",
  headers: { Authorization: `Bearer ${accessToken}` },
  body: form
});
```

브라우저가 multipart boundary를 생성하므로 요청 전체의 `Content-Type` 헤더는 직접 지정하지 않는다.

## 응답 및 저장 규칙

```json
{
  "status": 201,
  "message": "교범이 등록되었습니다.",
  "data": {
    "manualId": 1,
    "manualTitle": "분대 전술 교범",
    "trainingFieldId": 1,
    "manualDescription": "분대 전술 훈련 참고 자료",
    "manualStatus": "TEMPORARY_SAVED",
    "file": {
      "manualFileId": 1,
      "originalName": "교범.PPTX",
      "fileType": "PPT",
      "storageKey": "7ec2ef7948cb4a83992ed1ca644991bb1",
      "storedFileName": "7ec2ef7948cb4a83992ed1ca644991bb1.pptx",
      "sizeBytes": 102400
    }
  }
}
```

`storageKey`는 하이픈을 제거한 32자 UUID이며 DB 고유 제약으로 보호한다.
실제 파일명은 `{storageKey}.{판별된 소문자 확장자}`이다.
PPTX를 PPT로, DOCX를 DOC로 변환하지 않는다. 원본 바이트와 원본 파일명을 보존한다.
DB의 `fileType`은 PPT/PPTX를 `PPT`, DOC/DOCX를 `WORD`로 묶는다.

실제 파일 기본 위치는 `src/main/resources/static/originalManual`이며 `.staging`에 임시 저장한다.
DB 저장 또는 커밋이 롤백되면 최종 파일을 삭제하고, 임시 파일은 성공·실패 모두 정리한다.
`/originalManual/**` 직접 접근은 인증 사용자에게도 차단한다.

## 오류

| 상태 | 조건 |
| --- | --- |
| 400 | 입력 오류, 필수 파트 누락, 빈 파일, 실제 형식 판별 실패, 확장자 불일치, 비활성 분야 |
| 401 | 인증 실패 |
| 403 | 관리자 권한 없음 또는 비활성 계정 |
| 404 | 훈련 분야 또는 사용자 없음 |
| 413 | 파일 또는 전체 요청 크기 제한 초과 |
| 415 | 지원하지 않는 확장자 또는 Content-Type |
| 500 | 디스크 또는 DB 저장 실패 |

오류 응답은 기존 `GlobalResponse`의 `status`, `message` 형식이다.

## 설정 및 운영 반영

| 환경 변수 | 기본값 |
| --- | --- |
| `MANUAL_STORAGE_ROOT` | `./src/main/resources/static/originalManual` |
| `MANUAL_MAX_FILE_SIZE` | `50MB` |
| `MANUAL_MAX_REQUEST_SIZE` | `55MB` |

JAR 배포 시 저장 경로를 쓰기 가능한 외부 디렉터리나 영속 볼륨으로 설정한다.
JAR 내부의 classpath resource 디렉터리는 런타임 저장 경로로 사용하지 않는다.
원본 파일과 임시 파일은 Git 및 배포 번들에서 제외한다.

기존 운영 DB에는 `docs/sql/20261002_manual_file_storage_key_unique.sql`을 검토해 수동 적용한다.
중복 키 조회 결과가 없어야 하며 이미 고유 제약이 있다면 다시 추가하지 않는다.
로컬 `ddl-auto=update` 및 테스트 `create-drop`은 엔티티의 고유 제약을 반영한다.

강제 종료나 파일 삭제 실패로 남은 파일은 다음 절차로 점검한다.

1. 등록 요청이 진행 중이지 않은 유지보수 시점에 저장 경로를 확인한다.
2. 충분한 유예 시간(예: 24시간)이 지난 임시 파일과 원본 파일만 대상으로 한다.
3. 원본 파일명의 storageKey가 `manual_file`에 존재하는지 조회한다.
4. DB에서 참조되지 않는 파일만 정리한다. 트랜잭션 결과가 불명확하다는 로그가 있으면 DB 상태를 먼저 확인한다.

HWP 판별은 한컴 공개 HWP 5 규격의 OLE 컨테이너, FileHeader 서명·버전, DocInfo와 본문 구조를 사용한다.
참고: https://tech.hancom.com/python-hwp-parsing-1/

## 검증

```powershell
.\gradlew.bat test
```

교범 테스트는 실제 HTTP/JWT/JPA/파일 시스템을 사용하며 외부 Redis 경계만 대체한다.
DOC/HWP fixture는 본문 파싱용 샘플이 아닌 형식 판별용 OLE 구조다.
