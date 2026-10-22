CREATE TABLE IF NOT EXISTS trips (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  destination VARCHAR(120) NOT NULL,
  start_date DATE NULL,
  end_date DATE NULL,
  budget DECIMAL(12,2) NULL,
  travelers TINYINT UNSIGNED NOT NULL DEFAULT 2,
  notes VARCHAR(1000) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_trips_created_at (created_at),
  INDEX idx_trips_destination (destination),
  CONSTRAINT chk_trips_travelers CHECK (travelers BETWEEN 1 AND 30),
  CONSTRAINT chk_trips_budget CHECK (budget IS NULL OR budget >= 0),
  CONSTRAINT chk_trips_dates CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
