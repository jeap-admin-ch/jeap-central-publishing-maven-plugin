/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;

import ch.admin.bit.jeap.central.publishing.RetryClassifier.Decision;
import org.sonatype.central.publisher.client.httpclient.RequestType;
import org.sonatype.central.publisher.client.httpclient.auth.AuthProvider;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;

import static java.lang.String.format;
import static org.sonatype.central.publisher.client.PublisherConstants.UPLOAD_ENDPOINT_URL;

/**
 * Sends requests to the Central Publisher Portal, repeating them on transient failures.
 * <p>
 * The upstream plugin sends every request exactly once, with httpclient's default socket timeout of three minutes.
 * Uploading a release bundle regularly takes longer than that, which made releases fail with
 * {@code Invalid request. Read timed out} even though the portal had received the bundle. This executor therefore
 * <ul>
 *   <li>configures generous connect and socket timeouts (see {@link RetryConfig}),</li>
 *   <li>repeats failed requests with an exponential backoff, and</li>
 *   <li>records in {@link UploadRetryState} when a bundle upload had to be repeated although the portal might
 *       already have received it.</li>
 * </ul>
 * The request is rebuilt for every attempt instead of relying on httpclient's own retry handling, which by default
 * repeats neither {@code POST} requests nor read timeouts, and only handles repeatable request entities.
 */
public class RetryingHttpRequestExecutor
{
  private RetryingHttpRequestExecutor() {
  }

  public static String sendRequest(
      final AuthProvider authProvider,
      final String endpointUrl,
      final Map<String, String> params,
      final Path body,
      final RequestType requestType) throws IOException
  {
    RetryConfig config = RetryConfig.fromSystemProperties();
    URI uri = toUri(endpointUrl, params);
    boolean uploadRequest = endpointUrl.endsWith(UPLOAD_ENDPOINT_URL);
    int maxAttempts = config.getMaxAttempts();

    IOException lastFailure;
    for (int attempt = 1; ; attempt++) {
      Decision decision;
      try {
        return executeOnce(authProvider, uri, body, requestType, config);
      }
      catch (HttpResponseException e) {
        lastFailure = e;
        decision = RetryClassifier.classify(e.getStatusCode());
      }
      catch (IOException e) {
        lastFailure = e;
        decision = RetryClassifier.classify(e);
      }

      if (decision == Decision.FAIL || attempt >= maxAttempts) {
        if (decision != Decision.FAIL) {
          PublishingLog.warn(format("Giving up on %s after %d attempts: %s",
              uri.getPath(), attempt, describe(lastFailure)));
        }
        throw lastFailure;
      }

      if (decision == Decision.RETRY_AMBIGUOUS && uploadRequest) {
        if (!config.isRetryOnAmbiguousFailure()) {
          PublishingLog.warn(format(
              "Not repeating the bundle upload after '%s' because %s is set to false. The portal may or may not "
                  + "have received the bundle, please check the deployments in the portal.",
              describe(lastFailure), RetryConfig.RETRY_ON_AMBIGUOUS_FAILURE));
          throw lastFailure;
        }

        UploadRetryState.markAmbiguousUploadRetry();
        PublishingLog.warn(
            "The bundle upload failed in a way that leaves it open whether the Central Publisher Portal has already "
                + "received the bundle. Uploading it again may result in a second, duplicate deployment - check the "
                + "portal for leftover deployments after this build.");
      }

      long delayMillis = config.getRetryDelayMillis(attempt);
      PublishingLog.warn(format("Attempt %d of %d for %s failed (%s), retrying in %d seconds",
          attempt, maxAttempts, uri.getPath(), describe(lastFailure), delayMillis / 1000));

      try {
        Thread.sleep(delayMillis);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw lastFailure;
      }
    }
  }

  private static String executeOnce(
      final AuthProvider authProvider,
      final URI uri,
      final Path body,
      final RequestType requestType,
      final RetryConfig config) throws IOException
  {
    ClassicHttpRequest request = createRequest(authProvider, uri, body, requestType);

    try (CloseableHttpClient client = createClient(config)) {
      return client.execute(request, new BasicHttpClientResponseHandler());
    }
  }

  private static ClassicHttpRequest createRequest(
      final AuthProvider authProvider,
      final URI uri,
      final Path body,
      final RequestType requestType)
  {
    if (requestType == RequestType.POST) {
      HttpPost httpPost = new HttpPost(uri);
      authProvider.getAuthHeaders().forEach(httpPost::addHeader);

      if (body != null) {
        File file = body.toFile();
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.LEGACY);
        builder.addBinaryBody("bundle", file, ContentType.APPLICATION_OCTET_STREAM, file.getName());
        httpPost.setEntity(builder.build());
      }

      return httpPost;
    }

    HttpGet httpGet = new HttpGet(uri);
    authProvider.getAuthHeaders().forEach(httpGet::addHeader);
    return httpGet;
  }

  private static CloseableHttpClient createClient(final RetryConfig config) {
    Timeout connectTimeout = Timeout.ofSeconds(config.getConnectTimeoutSeconds());
    Timeout socketTimeout = Timeout.ofSeconds(config.getSocketTimeoutSeconds());

    // useSystemProperties(): Patched compared to upstream repo, honours the JVM's HTTP proxy system properties.
    // It has to be set on both builders, as the connection manager created here replaces the one that
    // HttpClients.createSystem() would have configured.
    PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
        .useSystemProperties()
        .setDefaultSocketConfig(SocketConfig.custom()
            .setSoTimeout(socketTimeout)
            .build())
        .setDefaultConnectionConfig(ConnectionConfig.custom()
            .setConnectTimeout(connectTimeout)
            .setSocketTimeout(socketTimeout)
            .build())
        .build();

    return HttpClients.custom()
        .useSystemProperties()
        .setConnectionManager(connectionManager)
        .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectionRequestTimeout(connectTimeout)
            .setResponseTimeout(socketTimeout)
            .build())
        // retries are handled here, so that they also cover POST requests and read timeouts
        .disableAutomaticRetries()
        .build();
  }

  private static URI toUri(final String endpointUrl, final Map<String, String> params) throws IOException {
    try {
      URIBuilder uriBuilder = new URIBuilder(endpointUrl);
      params.forEach(uriBuilder::addParameter);
      return uriBuilder.build();
    }
    catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  private static String describe(final IOException failure) {
    if (failure instanceof HttpResponseException) {
      return "HTTP " + ((HttpResponseException) failure).getStatusCode();
    }
    String message = failure.getMessage();
    return message != null ? message : failure.getClass().getSimpleName();
  }
}
