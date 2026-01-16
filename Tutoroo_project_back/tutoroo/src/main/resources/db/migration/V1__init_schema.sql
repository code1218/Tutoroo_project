SET NAMES utf8mb4;

-- 1. 사용자 테이블
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `name` VARCHAR(50),
    `gender` VARCHAR(10),
    `age` INT,
    `phone` VARCHAR(20),
    `email` VARCHAR(100) UNIQUE,
    `profile_image` VARCHAR(255),
    `parent_phone` VARCHAR(20),
    `provider` VARCHAR(20),
    `provider_id` VARCHAR(100),
    `role` VARCHAR(20) DEFAULT 'ROLE_USER',
    `status` VARCHAR(20) DEFAULT 'ACTIVE',
    `withdrawal_reason` TEXT,
    `deleted_at` DATETIME,
    `membership_tier` VARCHAR(20) DEFAULT 'BASIC',

    `total_point` INT DEFAULT 0 COMMENT '누적 랭킹 포인트 (감소 안 함)',
    `point_balance` INT DEFAULT 0 COMMENT '사용 가능 포인트 (지갑)',

    `daily_rank` INT DEFAULT 0,
    `level` INT DEFAULT 1,
    `exp` INT DEFAULT 0,
    `current_streak` INT DEFAULT 0,
    `last_study_date` DATE,
    `rival_id` BIGINT,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`rival_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 프롬프트 관리 테이블
CREATE TABLE IF NOT EXISTS `prompts` (
    `prompt_key` VARCHAR(50) PRIMARY KEY,
    `content` TEXT NOT NULL,
    `description` VARCHAR(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- [핵심] 선생님 5마리 페르소나 데이터 (고정)
INSERT IGNORE INTO `prompts` (`prompt_key`, `content`, `description`) VALUES
('CONSULT_SYSTEM', '너는 15년 경력의 입시 전문 컨설턴트야. 정중하고 전문적으로 상담해.', '초기 상담'),

-- 1. 호랑이 (엄격)
('TEACHER_TIGER', '너는 엄격한 호랑이 선생님이야. 학생이 나태해지면 따끔하게 혼내고, 반말을 사용하며 강력하게 리드해. "정신 안 차려?", "이것도 몰라?" 같은 말을 자주 써.', '호랑이 선생님'),

-- 2. 토끼 (속도)
('TEACHER_RABBIT', '너는 성격 급한 토끼 선생님이야. 서론은 생략하고 핵심만 빠르게 말해. "시간 없어! 빨리빨리!", "중요한 건 이거야!"라며 속도감 있게 가르쳐.', '토끼 선생님'),

-- 3. 거북이 (친절)
('TEACHER_TURTLE', '너는 인자한 거북이 선생님이야. 학생이 이해할 때까지 백 번이고 천 번이고 친절하게 설명해줘. 존댓말을 쓰고 "천천히 해도 괜찮아요", "잘하고 있어요"라고 격려해.', '거북이 선생님'),

-- 4. 캥거루 (열혈)
('TEACHER_KANGAROO', '너는 에너지가 넘치는 헬스장 관장님 같은 캥거루 선생님이야. 공부를 근육 단련하듯이 가르쳐. "원 투! 원 투! 뇌세포에 자극 들어간다!", "할 수 있다! 포기하지 마!"라고 외쳐.', '캥거루 선생님'),

-- 5. 동양용 (현자)
('TEACHER_EASTERN_DRAGON', '너는 천 년을 산 동양의 용 선생님이야. 아주 점잖고 고풍스러운 하오체를 써라. "그대의 배움이 깊어지고 있구려", "이치를 깨달아야 하오"라며 인생의 지혜를 함께 전달해.', '동양용 선생님'),

-- [추가] 비전/테스트 관련 공통 프롬프트
('TEST_ADAPTIVE', '[역할]: %s\n[상황]: 학생의 평균 점수는 %d점이야. 난이도 \'%s\'로 퀴즈를 하나 내줘.\n[주제]: %s (%d일차)\n문제와 보기만 깔끔하게 출력해.', '적응형 테스트 생성'),
('TEST_FEEDBACK', '주제: %s\n학생 답안: %s\n위 답안을 채점(100점 만점)하고, 틀린 부분이 있다면 친절하게 설명해줘. 마지막에 "점수: XX" 형식을 꼭 포함해.', '테스트 피드백'),
('VISION_FEEDBACK', '주제: %s. 이 이미지는 학생이 제출한 답안이거나 필기 내용이야. 내용을 분석해서 정답 여부를 판단하고 피드백을 줘.', '이미지 분석');


-- 3. 학습 플랜 테이블
CREATE TABLE IF NOT EXISTS `study_plans` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `goal` VARCHAR(255),
    `persona` VARCHAR(50) COMMENT '선생님 타입 (TIGER, KANGAROO 등)',
    `custom_tutor_name` VARCHAR(50),
    `roadmap_json` LONGTEXT,
    `start_date` DATE,
    `end_date` DATE,
    `progress_rate` DOUBLE DEFAULT 0.0,
    `status` VARCHAR(20) DEFAULT 'PROCEEDING',
    `is_paid` BOOLEAN DEFAULT FALSE,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 데일리 학습 로그
CREATE TABLE IF NOT EXISTS `study_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `plan_id` BIGINT NOT NULL,
    `study_date` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `day_count` INT,
    `content_summary` TEXT,
    `daily_summary` TEXT,
    `test_score` INT,
    `ai_feedback` TEXT,
    `student_feedback` TEXT,
    `point_change` INT,
    `is_completed` BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (`plan_id`) REFERENCES `study_plans`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 결제 정보
CREATE TABLE IF NOT EXISTS `payments` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `plan_id` BIGINT,
    `imp_uid` VARCHAR(100) UNIQUE,
    `merchant_uid` VARCHAR(100),
    `item_name` VARCHAR(100),
    `pay_method` VARCHAR(50),
    `pg_provider` VARCHAR(50),
    `amount` INT,
    `status` VARCHAR(20),
    `paid_at` DATETIME,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. TTS 캐시 테이블
CREATE TABLE IF NOT EXISTS `tts_cache` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `text_hash` VARCHAR(64) UNIQUE NOT NULL,
    `audio_path` VARCHAR(255) NOT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 펫 정보 테이블
CREATE TABLE IF NOT EXISTS `pet_info` (
    `pet_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `pet_name` VARCHAR(50),
    `pet_type` VARCHAR(50) NOT NULL,
    `stage` INT DEFAULT 1,
    `status` VARCHAR(20) DEFAULT 'ACTIVE',
    `fullness` INT DEFAULT 80,
    `intimacy` INT DEFAULT 50,
    `exp` INT DEFAULT 0,
    `cleanliness` INT DEFAULT 100,
    `stress` INT DEFAULT 0,
    `energy` INT DEFAULT 100,
    `is_sleeping` BOOLEAN DEFAULT FALSE,
    `equipped_items` JSON,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `last_fed_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `last_played_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `last_cleaned_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `last_slept_at` DATETIME,
    `birth_date` DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. 펫 성장 규칙
CREATE TABLE IF NOT EXISTS `pet_growth_rule` (
    `stage` INT PRIMARY KEY,
    `stage_name` VARCHAR(20),
    `required_exp` INT,
    `description` VARCHAR(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `pet_growth_rule` VALUES
(1, 'EGG', 100, '알 단계'),
(2, 'BABY', 600, '아기 단계'),
(3, 'TEEN', 1500, '청소년 단계'),
(4, 'ADULT', 3000, '어른 단계'),
(5, 'MASTER', 999999, '졸업 단계');

-- 9. 펫의 비밀 일기장
CREATE TABLE IF NOT EXISTS `pet_diary` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `pet_id` BIGINT NOT NULL,
    `date` DATE NOT NULL,
    `content` TEXT,
    `mood` VARCHAR(20),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`pet_id`) REFERENCES `pet_info`(`pet_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. 펫 스킬 정보
CREATE TABLE IF NOT EXISTS `pet_skills` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `pet_type` VARCHAR(50),
    `skill_name` VARCHAR(50),
    `skill_code` VARCHAR(50),
    `effect_value` DOUBLE,
    `description` VARCHAR(200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `pet_skills` (`pet_type`, `skill_name`, `skill_code`, `effect_value`, `description`) VALUES
('TIGER', '호랑이의 집중력', 'POINT_BOOST', 1.2, '포인트 획득량 20% 증가'),
('RABBIT', '토끼의 신속함', 'EXP_BOOST', 1.1, '경험치 획득량 10% 증가');