/*
 * Copyright (c) 2022-present Sonatype, Inc. All rights reserved.
 * "Sonatype" is a trademark of Sonatype, Inc.
 */

package org.sonatype.central.publisher.client.httpclient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import ch.admin.bit.jeap.central.publishing.RetryingHttpRequestExecutor;
import org.sonatype.central.publisher.client.httpclient.auth.AuthProvider;

public class PublisherHttpClient
{
  public static String sendRequest(
      final AuthProvider authProvider,
      final String endpointUrl,
      final Map<String, String> params,
      final Path body,
      final RequestType requestType) throws IOException
  {
    // Patched compared to upstream repo: the request is sent by the jEAP executor, which creates its HTTP client
    // with HttpClients.custom().useSystemProperties() so that the JVM's HTTP proxy system properties are honored,
    // applies generous connect/socket timeouts and repeats requests that failed transiently.
    return RetryingHttpRequestExecutor.sendRequest(authProvider, endpointUrl, params, body, requestType);
  }
}
