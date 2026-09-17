/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.sonatype.central.publisher.client.httpclient.RequestType;
import org.sonatype.central.publisher.client.httpclient.auth.AuthProvider;

import org.apache.hc.client5.http.HttpResponseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static ch.admin.bit.jeap.central.publishing.RetryConfig.CONNECT_TIMEOUT_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.MAX_RETRIES;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.RETRY_INITIAL_DELAY_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.RETRY_MAX_DELAY_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.RETRY_ON_AMBIGUOUS_FAILURE;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.SOCKET_TIMEOUT_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonatype.central.publisher.client.PublisherConstants.STATUS_ENDPOINT_URL;
import static org.sonatype.central.publisher.client.PublisherConstants.UPLOAD_ENDPOINT_URL;

class RetryingHttpRequestExecutorTest
{
  private static final String DEPLOYMENT_ID = "e6a1e1f0-0000-0000-0000-000000000001";

  private final StubPortal portal = new StubPortal();

  private AuthProvider authProvider;

  private Path bundleFile;

  @BeforeEach
  void setUp(@TempDir final Path tempDir) throws IOException {
    UploadRetryState.reset();

    System.setProperty(SOCKET_TIMEOUT_SECONDS, "1");
    System.setProperty(CONNECT_TIMEOUT_SECONDS, "2");
    System.setProperty(MAX_RETRIES, "3");
    System.setProperty(RETRY_INITIAL_DELAY_SECONDS, "0");
    System.setProperty(RETRY_MAX_DELAY_SECONDS, "0");

    authProvider = mock(AuthProvider.class);
    when(authProvider.getAuthHeaders()).thenReturn(new HashMap<>());

    bundleFile = tempDir.resolve("central-bundle.zip");
    Files.write(bundleFile, "not really a zip".getBytes(StandardCharsets.UTF_8));

    portal.start();
  }

  @AfterEach
  void tearDown() {
    portal.stop();

    System.clearProperty(SOCKET_TIMEOUT_SECONDS);
    System.clearProperty(CONNECT_TIMEOUT_SECONDS);
    System.clearProperty(MAX_RETRIES);
    System.clearProperty(RETRY_INITIAL_DELAY_SECONDS);
    System.clearProperty(RETRY_MAX_DELAY_SECONDS);
    System.clearProperty(RETRY_ON_AMBIGUOUS_FAILURE);
    UploadRetryState.reset();
  }

  @Test
  void uploadIsRepeatedUntilTheServerIsAvailableAgain() throws IOException {
    portal.onUpload(attempt -> attempt < 3
        ? StubPortal.Reply.status(503)
        : StubPortal.Reply.ok(DEPLOYMENT_ID));

    assertEquals(DEPLOYMENT_ID, upload());
    assertEquals(3, portal.uploadAttempts());
    assertFalse(UploadRetryState.wasAmbiguouslyRetried(),
        "a rejected upload cannot have been received by the portal");
  }

  @Test
  void uploadIsRepeatedAfterAReadTimeoutAndMarkedAsAmbiguous() throws IOException {
    portal.onUpload(attempt -> attempt == 1
        ? StubPortal.Reply.readTimeout()
        : StubPortal.Reply.ok(DEPLOYMENT_ID));

    assertEquals(DEPLOYMENT_ID, upload());
    assertEquals(2, portal.uploadAttempts());
    assertTrue(UploadRetryState.wasAmbiguouslyRetried(),
        "the portal may have received the bundle of the timed out attempt");
  }

  @Test
  void uploadIsNotRepeatedAfterAReadTimeoutIfAmbiguousRetriesAreDisabled() {
    System.setProperty(RETRY_ON_AMBIGUOUS_FAILURE, "false");
    portal.onUpload(attempt -> StubPortal.Reply.readTimeout());

    assertThrows(SocketTimeoutException.class, this::upload);

    assertEquals(1, portal.uploadAttempts());
    assertFalse(UploadRetryState.wasAmbiguouslyRetried());
  }

  @Test
  void uploadIsNotRepeatedOnAClientError() {
    portal.onUpload(attempt -> StubPortal.Reply.status(401));

    HttpResponseException failure = assertThrows(HttpResponseException.class, this::upload);

    assertEquals(401, failure.getStatusCode());
    assertEquals(1, portal.uploadAttempts());
    assertFalse(UploadRetryState.wasAmbiguouslyRetried());
  }

  @Test
  void uploadFailsAfterTheConfiguredNumberOfAttempts() {
    System.setProperty(MAX_RETRIES, "1");
    portal.onUpload(attempt -> StubPortal.Reply.status(503));

    assertThrows(HttpResponseException.class, this::upload);

    assertEquals(2, portal.uploadAttempts());
  }

  @Test
  void statusRequestIsRepeatedAfterAReadTimeoutWithoutMarkingTheUpload() throws IOException {
    portal.onStatus(attempt -> attempt == 1
        ? StubPortal.Reply.readTimeout()
        : StubPortal.Reply.ok(DEPLOYMENT_ID));

    String response = RetryingHttpRequestExecutor.sendRequest(
        authProvider, portal.baseUrl() + STATUS_ENDPOINT_URL, new HashMap<>(), null, RequestType.POST);

    assertEquals(DEPLOYMENT_ID, response);
    assertEquals(2, portal.statusRequests());
    assertFalse(UploadRetryState.wasAmbiguouslyRetried(),
        "only bundle uploads have a side effect worth warning about");
  }

  private String upload() throws IOException {
    Map<String, String> params = new HashMap<>();
    params.put("name", "a deployment");
    return RetryingHttpRequestExecutor.sendRequest(
        authProvider, portal.baseUrl() + UPLOAD_ENDPOINT_URL, params, bundleFile, RequestType.POST);
  }
}
