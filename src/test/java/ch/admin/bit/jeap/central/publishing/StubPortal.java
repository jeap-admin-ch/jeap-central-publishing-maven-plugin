/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.sonatype.central.publisher.client.model.DeploymentState;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.sonatype.central.publisher.client.PublisherConstants.PUBLISHED_ENDPOINT_URL;
import static org.sonatype.central.publisher.client.PublisherConstants.STATUS_ENDPOINT_URL;
import static org.sonatype.central.publisher.client.PublisherConstants.UPLOAD_ENDPOINT_URL;

/**
 * A minimal stand-in for the Central Publisher Portal, serving the three endpoints the plugin uses on a local port.
 * Each endpoint can answer differently per attempt, which is what makes the retry behaviour testable.
 */
class StubPortal
{
  /**
   * How long a {@link Reply#readTimeout()} stalls a request. Tests configure a socket timeout of one second, so the
   * client gives up while the stub is still "processing" the request - exactly the failure that made releases fail.
   */
  private static final long STALL_MILLIS = 2_000;

  static final String DEPLOYMENT_ID = "deployment-1";

  private final AtomicInteger uploadAttempts = new AtomicInteger();

  private final AtomicInteger statusRequests = new AtomicInteger();

  private final AtomicInteger publishedRequests = new AtomicInteger();

  private volatile IntFunction<Reply> uploadReply = attempt -> Reply.ok(DEPLOYMENT_ID);

  private volatile IntFunction<Reply> statusReply = attempt -> Reply.ok(deploymentStatus(DeploymentState.VALIDATED));

  private volatile IntFunction<Reply> publishedReply = attempt -> Reply.ok("{\"published\":false}");

  private HttpServer server;

  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(Executors.newFixedThreadPool(4));
    server.createContext(UPLOAD_ENDPOINT_URL, exchange -> handle(exchange, uploadAttempts, uploadReply));
    server.createContext(STATUS_ENDPOINT_URL, exchange -> handle(exchange, statusRequests, statusReply));
    server.createContext(PUBLISHED_ENDPOINT_URL, exchange -> handle(exchange, publishedRequests, publishedReply));
    server.start();
  }

  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  String baseUrl() {
    return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
  }

  void onUpload(final IntFunction<Reply> reply) {
    uploadReply = reply;
  }

  void onStatus(final IntFunction<Reply> reply) {
    statusReply = reply;
  }

  /**
   * Lets the status endpoint report the given state for every request.
   */
  void reportsDeploymentState(final DeploymentState state) {
    statusReply = attempt -> Reply.ok(deploymentStatus(state));
  }

  /**
   * Lets the published endpoint report whether the components of the deployment are on Maven Central.
   */
  void reportsPublished(final boolean published) {
    // the extra flag is intentional: the response is read into a map of booleans, and additional flags the portal
    // may report must not fail the build
    publishedReply = attempt -> Reply.ok(
        "{\"published\":" + published + ",\"aFlagThisPluginDoesNotKnow\":true}");
  }

  int uploadAttempts() {
    return uploadAttempts.get();
  }

  int statusRequests() {
    return statusRequests.get();
  }

  int publishedRequests() {
    return publishedRequests.get();
  }

  private static String deploymentStatus(final DeploymentState state) {
    // the unknown property is intentional: the portal keeps adding fields to its responses, and the client has to
    // tolerate them instead of failing the release on an unrecognized property
    return "{\"deploymentId\":\"" + DEPLOYMENT_ID + "\","
        + "\"deploymentName\":\"test-deployment\","
        + "\"deploymentState\":\"" + state.name() + "\","
        + "\"purls\":[\"pkg:maven/ch.admin.bit.jeap.test/publish-project@1.0.0\"],"
        + "\"warnings\":[\"a warning from the portal\"],"
        + "\"aPropertyThisPluginDoesNotKnow\":\"and does not have to know\","
        + "\"errors\":{\"validation\":[\"Component already published\"]}}";
  }

  private static void handle(
      final HttpExchange exchange,
      final AtomicInteger requestCount,
      final IntFunction<Reply> replies) throws IOException
  {
    int attempt = requestCount.incrementAndGet();
    drain(exchange.getRequestBody());

    Reply reply = replies.apply(attempt);
    if (reply.stall) {
      sleep();
    }

    byte[] body = reply.body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(reply.status, body.length == 0 ? -1 : body.length);
    try (OutputStream responseBody = exchange.getResponseBody()) {
      responseBody.write(body);
    }
  }

  private static void drain(final InputStream requestBody) throws IOException {
    byte[] buffer = new byte[8192];
    while (requestBody.read(buffer) >= 0) {
      // the client only gets to see a response once its request has been read completely
    }
  }

  private static void sleep() {
    try {
      Thread.sleep(STALL_MILLIS);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UncheckedIOException(new IOException("interrupted while stalling a request"));
    }
  }

  static class Reply
  {
    private final int status;

    private final String body;

    private final boolean stall;

    private Reply(final int status, final String body, final boolean stall) {
      this.status = status;
      this.body = body;
      this.stall = stall;
    }

    static Reply ok(final String body) {
      return new Reply(200, body, false);
    }

    static Reply status(final int status) {
      return new Reply(status, "", false);
    }

    /**
     * Answers so late that the client runs into a read timeout - the request itself is received in full.
     */
    static Reply readTimeout() {
      return new Reply(200, "", true);
    }
  }
}
