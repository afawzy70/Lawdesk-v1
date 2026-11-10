package com.lawdesk.platform;

import java.net.http.HttpClient;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * The same burst over HTTP/2 (cleartext h2c upgrade, Tomcat's HTTP/2 enabled). Production reaches the app over
 * HTTP/2 from the browser to the proxy; this isolates what HTTP/2 multiplexing changes for the application itself
 * with everything else (pool, data, requests) identical.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "server.http2.enabled=true")
class CasePageConcurrencyHttp2Test extends CasePageConcurrencyTest {

    @Override
    protected HttpClient.Version httpVersion() {
        return HttpClient.Version.HTTP_2;
    }
}
