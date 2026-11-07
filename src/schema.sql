CREATE TABLE IF NOT EXISTS accounts (
  account_id    INT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name  VARCHAR(50)  NOT NULL,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS levels (
  level_id   INT AUTO_INCREMENT PRIMARY KEY,
  level_name VARCHAR(50) NOT NULL UNIQUE,
  data       JSON NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS level_completions (
  completion_id   INT AUTO_INCREMENT PRIMARY KEY,
  account_id      INT NOT NULL,
  level_id        INT NOT NULL,
  completion_time DECIMAL(6,2) NOT NULL,
  completed_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  partner_id      INT NULL,
  FOREIGN KEY (account_id) REFERENCES accounts(account_id),
  FOREIGN KEY (level_id)   REFERENCES levels(level_id),
  FOREIGN KEY (partner_id) REFERENCES accounts(account_id),
  INDEX idx_lv_time (level_id, completion_time),
  INDEX idx_acc_lv (account_id, level_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS player_results (
  account_id        INT PRIMARY KEY,
  levels_completed  INT NOT NULL DEFAULT 0,
  total_plays       INT NOT NULL DEFAULT 0,
  last_completed_at DATETIME NULL,
  FOREIGN KEY (account_id) REFERENCES accounts(account_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS player_level_stats (
  account_id INT NOT NULL,
  level_id   INT NOT NULL,
  attempts   INT NOT NULL DEFAULT 0,
  PRIMARY KEY (account_id, level_id),
  FOREIGN KEY (account_id) REFERENCES accounts(account_id),
  FOREIGN KEY (level_id)   REFERENCES levels(level_id)
) ENGINE=InnoDB;
