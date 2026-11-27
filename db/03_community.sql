USE cheongyang_bus;

CREATE TABLE IF NOT EXISTS community_post (
    post_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(10) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    CONSTRAINT ck_community_category CHECK (category IN ('QUESTION', 'GENERAL', 'INFO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
