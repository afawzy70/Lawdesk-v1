package com.lawdesk.reports;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** {@code GET /api/v1/reports/cases} (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-031). */
@RestController
@RequestMapping("/api/v1/reports/cases")
class CaseInventoryController {

    private final CaseInventory inventory;

    CaseInventoryController(CaseInventory inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    CaseInventoryReport list(@Valid @ModelAttribute CaseInventoryQuery query) {
        return inventory.page(query);
    }
}
