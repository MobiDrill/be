-- 운영 반영 전 중복 조회 결과가 없는지 확인한다.
SELECT storage_key, COUNT(*)
FROM manual_file
GROUP BY storage_key
HAVING COUNT(*) > 1;

-- 기존 스키마에 동일 제약이 없는 경우에만 수동 적용한다.
ALTER TABLE manual_file
    ADD CONSTRAINT uk_manual_file_storage_key UNIQUE (storage_key);

-- 롤백: ALTER TABLE manual_file DROP INDEX uk_manual_file_storage_key;
