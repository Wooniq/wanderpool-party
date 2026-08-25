-- 참여 승낙 동시성 제어(낙관적 락)를 위한 버전 컬럼 추가
ALTER TABLE party
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
