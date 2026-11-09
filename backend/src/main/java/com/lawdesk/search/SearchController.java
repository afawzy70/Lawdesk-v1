package com.lawdesk.search;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/**
 * The unified search, LD-057 (requirements section 28). Whoever may read cases may search them and their parties; the other
 * record types need SEARCH_ALL as well. Every hit is restricted to what the user may see (D3).
 */
@RestController
@RequestMapping("/api/v1/search")
class SearchController {

    private final SearchService service;

    SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    SearchRequests.Result search(@Valid @ModelAttribute SearchRequests.Query query) {
        return service.search(query);
    }
}
