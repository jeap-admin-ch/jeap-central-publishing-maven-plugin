/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.sonatype.central.publisher.client.httpclient.RequestType;
import org.sonatype.central.publisher.client.httpclient.auth.AuthProvider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.HttpResponseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static ch.admin.bit.jeap.central.publishing.RetryConfig.CONNECT_TIMEOUT_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.MAX_RETRIES;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.RETRY_INITIAL_DELAY_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.RETRY_MAX_DELAY_SECONDS;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.RETRY_ON_AMBIGUOUS_FAILURE;
import static ch.admin.bit.jeap.central.publishing.RetryConfig.SOCKET_TIMEOUT_SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonatype.central.publisher.client.PublisherConstants.STATUS_ENDPOINT_URL;
import static org.sonatype.central.publisher.client.PublisherConstants.UPLOAD_ENDPOINT_URL;

public class RetryingHttpRequestExecutorTest
{
  private static final String DEPLOYMENT_ID = "e6a1e1f0-0000-0000-0000-000000000001";

  /**
   * Longer than the socket timeout configured below, so that the client runs into a read timeout.
   */
  private static final long LONGER_THAN_THE_SOCKET_TIMEOUT_MILLIS = 2_000;

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private HttpServer server;

  private final AtomicInteger requestCount = new AtomicInteger();

  private AuthProvider authProvider;

  private Path bundleFile;

  @Before
  public void setUp() throws IOException {
    UploadRetryState.reset();

    System.setProperty(SOCKET_TIMEOUT_SECONDS, "1");
    System.setProperty(CONNECT_TIMEOUT_SECONDS, "2");
    System.setProperty(MAX_RETRIES, "3");
    System.setProperty(RETRY_INITIAL_DELAY_SECONDS, "0");
    System.setProperty(RETRY_MAX_DELAY_SECONDS, "0");

    authProvider = mock(AuthProvider.class);
    when(authProvider.getAuthHeaders()).thenReturn(new HashMap<>());

    bundleFile = temporaryFolder.newFile("central-bundle.zip").toPath();
    Files.write(bundleFile, "not really a zip".getBytes(StandardCharsets.UTF_8));

    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(Executors.newFixedThreadPool(4));
    server.start();
  }

  @After
  public void tearDown() {
    server.stop(0);

    System.clearProperty(SOCKET_TIMEOUT_SECONDS);
    System.clearProperty(CONNECT_TIMEOUT_SECONDS);
    System.clearProperty(MAX_RETRIES);
    System.clearProperty(RETRY_INITIAL_DELAY_SECONDS);
    System.clearProperty(RETRY_MAX_DELAY_SECONDS);
    System.clearProperty(RETRY_ON_AMBIGUOUS_FAILURE);
    UploadRetryState.reset();
  }

  @Test
  public void uploadIsRepeatedUntilTheServerIsAvailableAgain() throws IOException {
    respondToUploads(attempt -> attempt < 3 ? 503 : 200);

    String response = upload();

    assertEquals(DEPLOYMENT_ID, response);
    assertEquals(3, requestCount.get());
    assertFalse("a rejected upload cannot have been received by the portal",
        UploadRetryState.wasAmbiguouslyRetried());
  }

  @Test
  public void uploadIsRepeatedAfterAReadTimeoutAndMarkedAsAmbiguous() throws IOException {
    respondToUploads(attempt -> {
      if (attempt == 1) {
        sleep(LONGER_THAN_THE_SOCKET_TIMEOUT_MILLIS);
      }
      return 200;
    });

    String response = upload();

    assertEquals(DEPLOYMENT_ID, response);
    assertEquals(2, requestCount.get());
    assertTrue("the portal may have received the bundle of the timed out attempt",
        UploadRetryState.wasAmbiguouslyRetried());
  }

  @Test
  public void uploadIsNotRepeatedAfterAReadTimeoutIfAmbiguousRetriesAreDisabled() {
    System.setProperty(RETRY_ON_AMBIGUOUS_FAILURE, "false");
    respondToUploads(attempt -> {
      sleep(LONGER_THAN_THE_SOCKET_TIMEOUT_MILLIS);
      return 200;
    });

    try {
      upload();
      fail("expected the read timeout to be propagated");
    }
    catch (IOException e) {
      assertTrue(e.getClass().getName(), e instanceof SocketTimeoutException);
    }

    assertEquals(1, requestCount.get());
    assertFalse(UploadRetryState.wasAmbiguouslyRetried());
  }

  @Test
  public void uploadIsNotRepeatedOnAClientError() {
    respondToUploads(attempt -> 401);

    try {
      upload();
      fail("expected the client error to be propagated");
    }
    catch (IOException e) {
      assertTrue(e.getClass().getName(), e instanceof HttpResponseException);
      assertEquals(401, ((HttpResponseException) e).getStatusCode());
    }

    assertEquals(1, requestCount.get());
    assertFalse(UploadRetryState.wasAmbiguouslyRetried());
  }

  @Test
  public void uploadFailsAfterTheConfiguredNumberOfAttempts() {
    System.setProperty(MAX_RETRIES, "1");
    respondToUploads(attempt -> 503);

    try {
      upload();
      fail("expected the last failure to be propagated");
    }
    catch (IOException e) {
      assertTrue(e.getClass().getName(), e instanceof HttpResponseException);
    }

    assertEquals(2, requestCount.get());
  }

  @Test
  public void statusRequestIsRepeatedAfterAReadTimeoutWithoutMarkingTheUpload() throws IOException {
    respond(STATUS_ENDPOINT_URL, attempt -> {
      if (attempt == 1) {
        sleep(LONGER_THAN_THE_SOCKET_TIMEOUT_MILLIS);
      }
      return 200;
    });

    String response = RetryingHttpRequestExecutor.sendRequest(
        authProvider, baseUrl() + STATUS_ENDPOINT_URL, new HashMap<>(), null, RequestType.POST);

    assertEquals(DEPLOYMENT_ID, response);
    assertEquals(2, requestCount.get());
    assertFalse("only bundle uploads have a side effect worth warning about",
        UploadRetryState.wasAmbiguouslyRetried());
  }

  private String upload() throws IOException {
    Map<String, String> params = new HashMap<>();
    params.put("name", "a deployment");
    return RetryingHttpRequestExecutor.sendRequest(
        authProvider, baseUrl() + UPLOAD_ENDPOINT_URL, params, bundleFile, RequestType.POST);
  }

  private void respondToUploads(final StatusCodeForAttempt statusCode) {
    respond(UPLOAD_ENDPOINT_URL, statusCode);
  }

  private void respond(final String path, final StatusCodeForAttempt statusCode) {
    server.createContext(path, exchange -> {
      int attempt = requestCount.incrementAndGet();
      consumeRequestBody(exchange);
      int status = statusCode.get(attempt);
      byte[] body = status == 200 ? DEPLOYMENT_ID.getBytes(StandardCharsets.UTF_8) : new byte[0];
      exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
  }

  private static void consumeRequestBody(final HttpExchange exchange) throws IOException {
    IOUtils.toByteArray(exchange.getRequestBody());
  }

  private static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private String baseUrl() {
    return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
  }

  @FunctionalInterface
  private interface StatusCodeForAttempt
  {
    int get(int attempt);
  }
}
