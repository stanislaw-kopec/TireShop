-- Authentication now uses Bearer JWT. Existing HTTP sessions cease to grant access.
DROP TABLE spring_session_attributes;
DROP TABLE spring_session;
