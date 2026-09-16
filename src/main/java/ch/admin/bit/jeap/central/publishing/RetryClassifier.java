/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

import org.apache.hc.client5.http.ConnectTimeoutException;

/**
 * Decides whether a failed request may be repeated, and whether repeating it could result in the request being
 * processed twice by the Central Publisher Portal.
 */
public class RetryClassifier
{
  public enum Decision
  {
    /**
     * The request certainly never reached the server, repeating it cannot have any side effect.
     */
    RETRY_SAFE,

    /**
     * The request may or may not have been processed by the server. Repeating it is safe for the status and published
     * endpoints, but may create a second deployment when uploading a bundle.
     */
    RETRY_AMBIGUOUS,

    /**
     * Repeating the request would fail again the same way.
     */
    FAIL
  }

  private RetryClassifier() {
  }

  public static Decision classify(final IOException exception) {
    // ConnectTimeoutException extends SocketTimeoutException and must therefore be checked first
    if (exception instanceof ConnectTimeoutException
        || exception instanceof ConnectException
        || exception instanceof UnknownHostException
        || exception instanceof NoRouteToHostException) {
      return Decision.RETRY_SAFE;
    }

    // Everything else - read timeouts (InterruptedIOException), connection resets, TLS problems (SSLException),
    // connections closed mid-request - happens once the request is on the wire, so we cannot tell what the server
    // has seen and have to treat it as ambiguous.
    return Decision.RETRY_AMBIGUOUS;
  }

  public static Decision classify(final int statusCode) {
    switch (statusCode) {
      case 429: // Too Many Requests
      case 502: // Bad Gateway
      case 503: // Service Unavailable
      case 504: // Gateway Timeout
        // the request was rejected or never made it through the gateway to the portal
        return Decision.RETRY_SAFE;
      case 408: // Request Timeout
      case 500: // Internal Server Error
        // the portal may have processed the request before or while failing
        return Decision.RETRY_AMBIGUOUS;
      default:
        // 4xx (bad request, invalid credentials, ...) and anything else will not get better by repeating it
        return Decision.FAIL;
    }
  }
}
