package com.shop.tire.demo;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("demo")
class DemoSeedRepository {
    private final JdbcTemplate jdbc;
    DemoSeedRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    void lock() { jdbc.execute("SELECT pg_advisory_xact_lock(71309422)"); }
    boolean applied() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from demo_seed_history where name='presentation-v1')", Boolean.class));
    }
    void record(Instant now) {
        jdbc.update("insert into demo_seed_history(name,applied_at) values ('presentation-v1',?)", Timestamp.from(now));
    }
}
