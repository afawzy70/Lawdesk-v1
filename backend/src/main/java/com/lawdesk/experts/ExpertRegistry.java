package com.lawdesk.experts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * The expert registry (LD-025): reference data like courts. Experts are never deleted, only deactivated, so referrals
 * keep naming them (ASSUMPTION; no experts are seeded).
 */
@Service
class ExpertRegistry {

    private static final String SELECT = """
            select id, name, specialty, office, phone, active, version, created_at, updated_at from expert""";

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentActor currentActor;

    ExpertRegistry(NamedParameterJdbcTemplate jdbc, CurrentActor currentActor) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    Page<ExpertRequests.Expert> list(Boolean active, String q, Pageable pageable) {
        StringBuilder where = new StringBuilder(" where true");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (active != null) {
            where.append(" and active = :active");
            params.addValue("active", active);
        }
        String query = Rules.trimToNull(q);
        if (query != null) {
            where.append(" and (name ilike :q or specialty ilike :q)");
            params.addValue("q", "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        Long total = jdbc.queryForObject("select count(*) from expert" + where, params, Long.class);
        params.addValue("offset", pageable.getOffset()).addValue("limit", pageable.getPageSize());
        List<ExpertRequests.Expert> items = jdbc.query(SELECT + where
                + " order by lower(name), id offset :offset limit :limit", params, (rs, n) -> row(rs));
        return new PageImpl<>(items, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()),
                total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    Optional<ExpertRequests.Expert> find(UUID id) {
        return jdbc.query(SELECT + " where id = :id", new MapSqlParameterSource("id", id), (rs, n) -> row(rs))
                .stream().findFirst();
    }

    ExpertRequests.Expert get(UUID id) {
        return find(id).orElseThrow(() -> new NotFoundException("expert"));
    }

    @Transactional
    ExpertRequests.Expert create(ExpertRequests.ExpertInput input) {
        ActorRef actor = currentActor.require();
        return jdbc.queryForObject("""
                insert into expert (name, specialty, office, phone, created_by, updated_by)
                values (:name, :specialty, :office, :phone, :actor, :actor)
                returning id, name, specialty, office, phone, active, version, created_at, updated_at""",
                new MapSqlParameterSource().addValue("name", input.name()).addValue("specialty", input.specialty())
                        .addValue("office", input.office()).addValue("phone", input.phone())
                        .addValue("actor", actor.id()),
                (rs, n) -> row(rs));
    }

    @Transactional
    ExpertRequests.Expert update(UUID id, ExpertRequests.ExpertUpdate input) {
        ActorRef actor = currentActor.require();
        get(id);
        List<ExpertRequests.Expert> updated = jdbc.query("""
                update expert set name = :name, specialty = :specialty, office = :office, phone = :phone,
                       active = :active, version = version + 1, updated_at = now(), updated_by = :actor
                where id = :id and version = :version
                returning id, name, specialty, office, phone, active, version, created_at, updated_at""",
                new MapSqlParameterSource().addValue("id", id).addValue("version", input.version())
                        .addValue("name", input.name()).addValue("specialty", input.specialty())
                        .addValue("office", input.office()).addValue("phone", input.phone())
                        .addValue("active", input.active()).addValue("actor", actor.id()),
                (rs, n) -> row(rs));
        if (updated.isEmpty()) {
            throw new ConflictException("expert was changed by someone else");
        }
        return updated.getFirst();
    }

    private static ExpertRequests.Expert row(ResultSet rs) throws SQLException {
        return new ExpertRequests.Expert(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("specialty"), rs.getString("office"), rs.getString("phone"), rs.getBoolean("active"),
                rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
