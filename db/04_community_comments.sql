USE cheongyang_bus;
SET @add_community_author = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'community_post' AND column_name = 'author_id'
    ),
    'SELECT 1',
    'ALTER TABLE community_post ADD COLUMN author_id VARCHAR(30) NULL'
);
PREPARE add_community_author FROM @add_community_author;
EXECUTE add_community_author;
DEALLOCATE PREPARE add_community_author;

CREATE TABLE IF NOT EXISTS community_comment (
    comment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    author_id VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    FOREIGN KEY (post_id) REFERENCES community_post(post_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES community_comment(comment_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_notification (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient VARCHAR(30) NOT NULL,
    comment_id BIGINT NOT NULL,
    UNIQUE KEY uq_comment_recipient (comment_id, recipient),
    KEY idx_recipient_notification (recipient, notification_id),
    FOREIGN KEY (comment_id) REFERENCES community_comment(comment_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;