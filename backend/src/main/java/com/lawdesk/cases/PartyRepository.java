package com.lawdesk.cases;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lawdesk.api.PageResponse;

/** SQL access to {@code party} and {@code case_party} (LD-016). */
@Repository
class PartyRepository {

    private static final String COLUMNS = """
            id, kind, name, national_id, registration_no, phone, email, address, version, created_at, created_by,
            updated_at, updated_by, authority, confidential, notes""";

    private final JdbcClient jdbc;

    PartyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Party insert(PartyRequest request, UUID actor) {
        return jdbc.sql("""
                        insert into party (kind, name, national_id, registration_no, phone, email, address,
                                           confidential, notes, created_by, updated_by)
                        values (:kind, :name, :nationalId, :registrationNo, :phone, :email, :address, :confidential,
                                :notes, :actor, :actor)
                        returning\s""" + COLUMNS)
                .param("kind", request.kind()).param("name", request.name()).param("nationalId", request.nationalId())
                .param("registrationNo", request.registrationNo()).param("phone", request.phone())
                .param("email", request.email()).param("address", request.address())
                .param("confidential", request.confidential()).param("notes", request.notes()).param("actor", actor)
                .query(PartyRepository::row).single();
    }

    /** Applies an edit if {@code request.version()} is still current; empty otherwise. */
    Optional<Party> update(UUID id, PartyRequest request, UUID actor) {
        return jdbc.sql("""
                        update party
                        set kind = :kind, name = :name, national_id = :nationalId, registration_no = :registrationNo,
                            phone = :phone, email = :email, address = :address, confidential = :confidential,
                            notes = :notes, version = version + 1,
                            updated_at = now(), updated_by = :actor
                        where id = :id and version = :version
                        returning\s""" + COLUMNS)
                .param("id", id).param("version", request.version())
                .param("kind", request.kind()).param("name", request.name()).param("nationalId", request.nationalId())
                .param("registrationNo", request.registrationNo()).param("phone", request.phone())
                .param("email", request.email()).param("address", request.address())
                .param("confidential", request.confidential()).param("notes", request.notes()).param("actor", actor)
                .query(PartyRepository::row).optional();
    }

    Optional<Party> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from party where id = :id").param("id", id)
                .query(PartyRepository::row).optional();
    }

    boolean exists(UUID id) {
        return jdbc.sql("select exists (select 1 from party where id = :id)").param("id", id)
                .query(Boolean.class).single();
    }

    boolean nationalIdTaken(String nationalId, UUID except) {
        return jdbc.sql("""
                        select exists (select 1 from party where national_id = :nationalId
                                       and (cast(:except as uuid) is null or id <> :except))""")
                .param("nationalId", nationalId).param("except", except).query(Boolean.class).single();
    }

    /** Parties whose name or identifiers contain {@code q} (ignoring case), by name. */
    PageResponse<Party> search(Paging paging, boolean byIdentifier) {
        String where = paging.q() == null ? "" : " where name ilike :q escape '\\'"
                + (byIdentifier ? " or national_id ilike :q escape '\\' or registration_no ilike :q escape '\\'" : "");
        String pattern = paging.q() == null ? null : "%" + Text.escapeLike(paging.q()) + "%";
        long total = jdbc.sql("select count(*) from party" + where).param("q", pattern).query(Long.class).single();
        List<Party> items = jdbc.sql("select " + COLUMNS + " from party" + where
                        + " order by lower(name), id limit :limit offset :offset")
                .param("q", pattern).param("limit", paging.sizeOrDefault()).param("offset", paging.offset())
                .query(PartyRepository::row).list();
        return paging.page(items, total);
    }

    void link(UUID caseId, UUID partyId, PartyRole role, Side side, UUID actor) {
        jdbc.sql("""
                        insert into case_party (case_id, party_id, role, side, created_by)
                        values (:caseId, :partyId, :role, :side, :actor)""")
                .param("caseId", caseId).param("partyId", partyId).param("role", role.name())
                .param("side", side == null ? null : side.name()).param("actor", actor)
                .update();
    }

    /**
     * The authority party (الهيئة): the one organisation row marked {@code authority}, created on first use and
     * renamed when the configured name changes. A concurrent first use waits on the unique index and reads the row.
     */
    UUID authorityParty(String name, UUID actor) {
        jdbc.sql("""
                        insert into party (kind, name, authority, created_by, updated_by)
                        values ('ORGANIZATION', :name, true, :actor, :actor)
                        on conflict ((true)) where authority do nothing""")
                .param("name", name).param("actor", actor).update();
        UUID id = jdbc.sql("select id from party where authority").query(UUID.class).single();
        jdbc.sql("""
                        update party set name = :name, version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and name <> :name""")
                .param("id", id).param("name", name).param("actor", actor).update();
        return id;
    }

    /** Removes the link and returns what it was, or empty when the party was not on the case. */
    Optional<CaseParty> unlink(UUID caseId, UUID partyId) {
        Optional<CaseParty> linked = linked(caseId, partyId);
        linked.ifPresent(link -> jdbc.sql("delete from case_party where case_id = :caseId and party_id = :partyId")
                .param("caseId", caseId).param("partyId", partyId).update());
        return linked;
    }

    Optional<CaseParty> linked(UUID caseId, UUID partyId) {
        return jdbc.sql(CASE_PARTIES + " where cp.case_id = :caseId and cp.party_id = :partyId")
                .param("caseId", caseId).param("partyId", partyId).query(PartyRepository::caseParty).optional();
    }

    /** The parties of a case in the order they were added. */
    List<CaseParty> ofCase(UUID caseId) {
        return jdbc.sql(CASE_PARTIES + " where cp.case_id = :caseId order by cp.created_at, p.name")
                .param("caseId", caseId).query(PartyRepository::caseParty).list();
    }

    private static final String CASE_PARTIES = """
            select p.id, p.kind, p.name, cp.role, cp.created_at, cp.side, p.authority
            from case_party cp join party p on p.id = cp.party_id""";

    private static CaseParty caseParty(ResultSet rs, int rowNum) throws SQLException {
        return new CaseParty(rs.getObject("id", UUID.class), PartyKind.valueOf(rs.getString("kind")),
                rs.getString("name"), PartyRole.valueOf(rs.getString("role")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getString("side") == null ? null : Side.valueOf(rs.getString("side")),
                rs.getBoolean("authority"));
    }

    private static Party row(ResultSet rs, int rowNum) throws SQLException {
        return new Party(
                rs.getObject("id", UUID.class),
                PartyKind.valueOf(rs.getString("kind")),
                rs.getString("name"),
                rs.getString("national_id"),
                rs.getString("registration_no"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("address"),
                rs.getLong("version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("created_by", UUID.class),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_by", UUID.class),
                rs.getBoolean("authority"),
                rs.getBoolean("confidential"),
                rs.getString("notes"));
    }

    /** The cases a party is on, among those {@code visible} allows, newest first. */
    List<PartyCase> casesOf(UUID partyId, CaseAccessPolicy.Predicate visible) {
        java.util.Map<String, Object> params = new java.util.HashMap<>(visible.params());
        params.put("party", partyId);
        return jdbc.sql("""
                        select c.id, c.reference_no, c.title, c.status, cp.role, cp.side
                        from case_party cp join legal_case c on c.id = cp.case_id
                        where cp.party_id = :party and\s""" + visible.sql() + " order by c.created_at desc, c.id")
                .params(params).query((rs, n) -> new PartyCase(rs.getObject("id", UUID.class),
                        rs.getString("reference_no"), rs.getString("title"), rs.getString("status"),
                        rs.getString("role"), rs.getString("side"))).list();
    }

    /** A case a party is on, with the party's role and side there. */
    record PartyCase(UUID caseId, String referenceNo, String title, String status, String role, String side) {
    }
}
