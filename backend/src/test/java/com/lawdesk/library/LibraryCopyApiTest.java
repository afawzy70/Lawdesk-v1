package com.lawdesk.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.lawdesk.documents.ArchiveFiles;

/** The stored copy of a library judgment (V842): archive rules, history, masking of a restricted judgment. */
@SpringBootTest
@AutoConfigureMockMvc
class LibraryCopyApiTest extends LibrarySupport {

    @Autowired
    MockMvc client;

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", ArchiveFiles.pdfWithText("SYNTH-DOC " + name));
    }

    @Test
    void aCopyIsStoredThroughTheArchiveRulesAndReplacedWithHistory() throws Exception {
        UUID judgment = register("SYNTH-DOC copy subject");
        client.perform(multipart("/api/v1/library/judgments/" + judgment + "/copy").file(pdf("one.pdf")).with(MANAGER).with(csrf()))
                .andExpect(status().isCreated());
        client.perform(multipart("/api/v1/library/judgments/" + judgment + "/copy").file(pdf("two.pdf")).with(MANAGER).with(csrf()))
                .andExpect(status().isCreated());
        assertThat(count("select count(*) from library_judgment_file where judgment_id = ?", judgment)).isEqualTo(2);
        assertThat(count("select count(*) from library_judgment_file where judgment_id = ? and removed_at is null", judgment))
                .isEqualTo(1);
        var download = client.perform(getAs(READER, "/api/v1/library/judgments/" + judgment + "/copy"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(download.getHeader("Content-Disposition")).contains("attachment").contains("two.pdf");
        assertThat(download.getContentAsByteArray()).startsWith("%PDF-".getBytes());
        assertThat(audited("LIBRARY_JUDGMENT_COPY_STORE", "LIBRARY_JUDGMENT_COPY_DOWNLOAD")).isEqualTo(3);
    }

    @Test
    void aReaderCannotStoreAndANonPdfDisguisedAsPdfIsRefused() throws Exception {
        UUID judgment = register("SYNTH-DOC copy refused");
        client.perform(multipart("/api/v1/library/judgments/" + judgment + "/copy").file(pdf("a.pdf")).with(READER).with(csrf()))
                .andExpect(status().isForbidden());
        MockMultipartFile fake = new MockMultipartFile("file", "x.pdf", "application/pdf", "not a pdf".getBytes());
        client.perform(multipart("/api/v1/library/judgments/" + judgment + "/copy").file(fake).with(MANAGER).with(csrf()))
                .andExpect(status().isBadRequest());
        client.perform(getAs(READER, "/api/v1/library/judgments/" + judgment + "/copy")).andExpect(status().isNotFound());
    }

    @Test
    void theCopyOfARestrictedJudgmentIsHiddenFromReaders() throws Exception {
        UUID judgment = register("SYNTH-DOC restricted copy", "confidentiality", "RESTRICTED");
        client.perform(multipart("/api/v1/library/judgments/" + judgment + "/copy").file(pdf("r.pdf")).with(MANAGER).with(csrf()))
                .andExpect(status().isCreated());
        client.perform(getAs(READER, "/api/v1/library/judgments/" + judgment + "/copy")).andExpect(status().isNotFound());
        client.perform(getAs(MANAGER, "/api/v1/library/judgments/" + judgment + "/copy")).andExpect(status().isOk());
    }
}
