CREATE TABLE urls (shortcut VARCHAR PRIMARY KEY, url VARCHAR(255), uid VARCHAR(6))
CREATE TABLE visits (shortcut VARCHAR, referer VARCHAR, useragent VARCHAR)
CREATE TABLE users (uid VARCHAR(6) PRIMARY KEY, hash VARCHAR, domain VARCHAR)
CREATE INDEX shortcut_idx on visits (shortcut)
