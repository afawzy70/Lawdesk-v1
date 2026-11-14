package com.lawdesk.pleadings;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/**
 * The administrator's page of dropdown lists (LD-DOC-r5): the catalogue of every list, and the options of the lists kept
 * in {@code lookup_value}. The managed settlement, directive and template lists and the courts keep their own services
 * and permissions; the catalogue only names them.
 */
@RestController
class ListAdminController {

    record ListView(String code, String source, boolean addable, String titleAr, String titleEn, String noteAr,
            String noteEn, long options) {
    }

    private final LookupService service;
    private final JdbcClient jdbc;

    ListAdminController(LookupService service, JdbcClient jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/admin/lists")
    @PreAuthorize(Permission.Codes.LIST_ADMIN)
    List<ListView> lists() {
        return ListCatalog.ALL.stream().map((d) -> new ListView(d.code(), d.source().name(), d.addable(), d.titleAr(),
                d.titleEn(), d.noteAr(), d.noteEn(), count(d))).toList();
    }

    @GetMapping("/api/v1/admin/lists/{list}/options")
    @PreAuthorize(Permission.Codes.LIST_ADMIN)
    List<LookupRequests.View> options(@PathVariable String list) {
        return service.list(list, true);
    }

    @PostMapping("/api/v1/admin/lists/{list}/options")
    @PreAuthorize(Permission.Codes.LIST_ADMIN)
    ResponseEntity<LookupRequests.View> create(@PathVariable String list, @Valid @RequestBody LookupRequests.Create request) {
        LookupRequests.View created = service.create(list, request);
        return ResponseEntity.created(java.net.URI.create("/api/v1/admin/lists/" + list + "/options/" + created.id()))
                .body(created);
    }

    @PutMapping("/api/v1/admin/lists/options/{id}")
    @PreAuthorize(Permission.Codes.LIST_ADMIN)
    LookupRequests.View update(@PathVariable UUID id, @Valid @RequestBody LookupRequests.Update request) {
        return service.update(id, request);
    }

    @GetMapping("/api/v1/admin/lists/options/{id}/history")
    @PreAuthorize(Permission.Codes.LIST_ADMIN)
    List<LookupRequests.History> history(@PathVariable UUID id) {
        return service.history(id);
    }

    private long count(ListCatalog.Def def) {
        if (def.source() == ListCatalog.Source.COURT) {
            return jdbc.sql("select count(*) from court").query(Long.class).single();
        }
        String table = def.source() == ListCatalog.Source.LOOKUP ? "lookup_value" : "managed_list_value";
        return jdbc.sql("select count(*) from " + table + " where list_code = :list").param("list", def.code())
                .query(Long.class).single();
    }
}
