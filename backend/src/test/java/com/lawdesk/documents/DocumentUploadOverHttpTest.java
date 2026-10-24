package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Browser;
import com.lawdesk.security.TestUsers;

/**
 * LD-026 uploads through the real servlet container, with a real login, session cookie and CSRF token: the container's
 * multipart limit (10 MiB) answers an oversized file with a 400 problem naming the file, never a 500, and the
 * permission is checked before the body is read (QAD-026).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestUsers.class)
class DocumentUploadOverHttpTest extends PostgresTestSupport {

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final String BOUNDARY = "ld026boundary";

    @LocalServerPort
    int port;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private Browser signedIn(String role) {
        TestUsers.Created user = users.create(role);
        Browser browser = new Browser(port);
        assertThat(browser.login(user.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(200);
        return browser;
    }

    private String createCase(Browser browser) {
        HttpResponse<String> created = browser.post("/api/v1/cases",
                "{\"title\":\"Upload over HTTP\",\"caseType\":\"CIVIL\",\"openedOn\":\"2024-01-10\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        return JsonPath.read(created.body(), "$.id");
    }

    private HttpResponse<String> upload(Browser browser, String caseId, byte[] content) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/cases/" + caseId
                        + "/documents"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("Accept", "application/json")
                .header("Cookie", "JSESSIONID=" + browser.cookie("JSESSIONID") + "; XSRF-TOKEN="
                        + browser.cookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", browser.cookie("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] pdfOfSize(int size) {
        byte[] bytes = new byte[size];
        java.util.Arrays.fill(bytes, (byte) ' ');
        System.arraycopy("%PDF-".getBytes(StandardCharsets.ISO_8859_1), 0, bytes, 0, 5);
        return bytes;
    }

    @Test
    void anUploadOverTheLimitIsACleanProblemAndStoresNothing() throws Exception {
        Browser staff = signedIn("LEGAL_STAFF");
        String caseId = createCase(staff);

        HttpResponse<String> small = upload(staff, caseId, pdfOfSize(1024));
        assertThat(small.statusCode()).isEqualTo(201);

        HttpResponse<String> big = upload(staff, caseId, pdfOfSize(10 * 1024 * 1024 + 1));
        assertThat(big.statusCode()).isEqualTo(400);
        assertThat(big.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat((String) JsonPath.read(big.body(), "$.errors[0].field")).isEqualTo("file");
        assertThat((String) JsonPath.read(big.body(), "$.errors[0].code")).isEqualTo("MaxSize");
        assertThat(jdbc.queryForObject("select count(*) from case_document where case_id = ?::uuid", Integer.class,
                caseId)).isEqualTo(1);
        jdbc.update("delete from case_document where case_id = ?::uuid", caseId);
    }

    @Test
    void aUserWithoutTheManagePermissionIsRefusedBeforeTheBodyIsRead() throws Exception {
        Browser staff = signedIn("LEGAL_STAFF");
        String caseId = createCase(staff);
        Browser outsider = signedIn(null);
        HttpResponse<String> refused = upload(outsider, caseId, pdfOfSize(2048));
        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from case_document where case_id = ?", Integer.class,
                UUID.fromString(caseId))).isZero();
    }
}
