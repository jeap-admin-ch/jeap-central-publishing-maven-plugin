/*
 * Copyright (c) 2026-present Federal Office of Information Technology, Systems and Telecommunication FOITT.
 *
 * jEAP addition, not present in the upstream central-publishing-maven-plugin.
 */
package ch.admin.bit.jeap.central.publishing;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

import ch.admin.bit.jeap.central.publishing.RetryClassifier.Decision;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RetryClassifierTest
{
  @Test
  public void failuresBeforeTheRequestIsSentAreSafeToRetry() {
    assertEquals(Decision.RETRY_SAFE, RetryClassifier.classify(new ConnectException("refused")));
    assertEquals(Decision.RETRY_SAFE, RetryClassifier.classify(new HttpHostConnectException("refused")));
    assertEquals(Decision.RETRY_SAFE, RetryClassifier.classify(new UnknownHostException("central.sonatype.com")));
    // ConnectTimeoutException extends SocketTimeoutException, but the connection was never established
    assertEquals(Decision.RETRY_SAFE, RetryClassifier.classify(new ConnectTimeoutException("connect timed out")));
  }

  @Test
  public void failuresWhileTheRequestIsOnTheWireAreAmbiguous() {
    assertEquals(Decision.RETRY_AMBIGUOUS, RetryClassifier.classify(new SocketTimeoutException("Read timed out")));
    assertEquals(Decision.RETRY_AMBIGUOUS, RetryClassifier.classify(new SocketException("Connection reset")));
    assertEquals(Decision.RETRY_AMBIGUOUS, RetryClassifier.classify(new SSLException("handshake failed")));
    assertEquals(Decision.RETRY_AMBIGUOUS, RetryClassifier.classify(new NoHttpResponseException("no response")));
    assertEquals(Decision.RETRY_AMBIGUOUS, RetryClassifier.classify(new IOException("something else")));
  }

  @Test
  public void gatewayAndThrottlingResponsesAreSafeToRetry() {
    assertEquals(Decision.RETRY_SAFE, RetryClassifier.classify(429));
    assertEquals(Decision.RETRY_SAFE, RetryClassifier.classify(502));
    assertEquals(Decision.RETRY_SAFE, RetryClassifier.classify(503));
    assertEquals(Decision.RETRY_SAFE, RetryClassifier.classify(504));
  }

  @Test
  public void serverErrorsAreAmbiguous() {
    assertEquals(Decision.RETRY_AMBIGUOUS, RetryClassifier.classify(408));
    assertEquals(Decision.RETRY_AMBIGUOUS, RetryClassifier.classify(500));
  }

  @Test
  public void clientErrorsAreNotRetried() {
    assertEquals(Decision.FAIL, RetryClassifier.classify(400));
    assertEquals(Decision.FAIL, RetryClassifier.classify(401));
    assertEquals(Decision.FAIL, RetryClassifier.classify(403));
    assertEquals(Decision.FAIL, RetryClassifier.classify(404));
    assertEquals(Decision.FAIL, RetryClassifier.classify(422));
  }
}
