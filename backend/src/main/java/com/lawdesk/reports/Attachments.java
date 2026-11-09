package com.lawdesk.reports;

import java.util.Locale;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/** An export as a download, audited as {@code REPORT_EXPORT} with the report and format only (never the data or filters). */
@Component
class Attachments {

    private final AuditLog audit;
    private final CurrentActor actors;

    Attachments(AuditLog audit, CurrentActor actors) {
        this.audit = audit;
        this.actors = actors;
    }

    ResponseEntity<byte[]> of(ReportExports.ExportFile file, ExportQuery export) {
        ActorRef actor = actors.require();
        audit.record("REPORT_EXPORT", AuditLog.Outcome.SUCCESS, actor.id(), actor.username(), "REPORT",
                file.report() + ":" + export.format().toUpperCase(Locale.ROOT), null);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }
}
