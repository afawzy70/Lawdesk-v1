package com.lawdesk.integrations;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** What future integrations exist and that none is connected (requirements section 34). Read-only: nothing here can enable one. */
@RestController
@RequestMapping("/api/v1/integrations")
class IntegrationController {

    private final IntegrationRegistry registry;

    IntegrationController(IntegrationRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.USER_VIEW)
    List<IntegrationAdapter.Descriptor> list() {
        return registry.descriptors();
    }
}
