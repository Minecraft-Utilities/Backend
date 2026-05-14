-- findByUsernameIgnoreCase: Hibernate generates UPPER(username) = UPPER(?)
CREATE INDEX idx_players_username_upper ON players (UPPER(username));
