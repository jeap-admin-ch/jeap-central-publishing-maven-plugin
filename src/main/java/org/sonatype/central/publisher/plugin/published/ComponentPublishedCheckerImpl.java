/*
 * Copyright (c) 2022-present Sonatype, Inc. All rights reserved.
 * "Sonatype" is a trademark of Sonatype, Inc.
 */

package org.sonatype.central.publisher.plugin.published;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.central.publisher.client.PublisherClient;
import org.sonatype.central.publisher.client.PublisherClientFactory;

import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.logging.console.ConsoleLogger;

@Named
@Singleton
public class ComponentPublishedCheckerImpl
    extends AbstractLogEnabled
    implements ComponentPublishedChecker
{
  @Inject
  private PublisherClient publisherClient;

  public ComponentPublishedCheckerImpl() {
  }

  public ComponentPublishedCheckerImpl(final PublisherClient publisherClient) {
    this.publisherClient = publisherClient != null ? publisherClient : PublisherClientFactory.createPublisherClient();
    if (this.getLogger() == null) {
      this.enableLogging(new ConsoleLogger());
    }
  }

  @Override
  public boolean isComponentPublished(final String groupId, final String artifactId, final String version) {
    getLogger().info(
        "Check component published status for component: groupId:" + groupId + " artifactId:" + artifactId +
            " version:" + version);
    boolean published = publisherClient.isPublished(groupId, artifactId, version);
    if (published) {
      getLogger().info("Excluding component: groupId:" + groupId + " artifactId:" + artifactId + " version:" + version +
          " as a published");
    }
    return published;
  }
}
