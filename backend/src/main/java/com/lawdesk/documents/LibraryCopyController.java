package com.lawdesk.documents;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lawdesk.security.Permission;

/** The stored copy of a library judgment (section 16): LIBRARY_VIEW reads, LIBRARY_MANAGE stores and removes. */
@RestController
@RequestMapping("/api/v1/library/judgments/{id}/copy")
class LibraryCopyController {

    private final LibraryCopyService service;

    LibraryCopyController(LibraryCopyService service) {
        this.service = service;
    }

    @GetMapping("/info")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    LibraryCopyService.Copy info(@PathVariable UUID id) {
        return service.current(id);
    }

    @GetMapping("/history")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    List<LibraryCopyService.Copy> history(@PathVariable UUID id) {
        return service.history(id);
    }

    /** The multipart body is read lazily, so the permission is checked before the upload is parsed. */
    @PostMapping
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    ResponseEntity<LibraryCopyService.Copy> store(@PathVariable UUID id, HttpServletRequest request,
            @RequestPart(name = "file", required = false) MultipartFile file) throws HttpMediaTypeNotSupportedException {
        String type = request.getContentType();
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            throw new HttpMediaTypeNotSupportedException("the copy is uploaded as multipart/form-data");
        }
        return ResponseEntity.status(201).body(service.store(id, file));
    }

    @DeleteMapping
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    ResponseEntity<Void> remove(@PathVariable UUID id) {
        service.remove(id);
        return ResponseEntity.noContent().build();
    }

    /** Always a download, never rendered by the browser: attachment, nosniff and no caching. */
    @GetMapping
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    ResponseEntity<byte[]> download(@PathVariable UUID id, @RequestParam(name = "copyId", required = false) UUID copyId) {
        LibraryCopyService.Download found = service.download(id, copyId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(found.copy().mediaType()))
                .contentLength(found.content().length).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(found.copy().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(found.content());
    }
}
