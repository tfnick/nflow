package io.nflow.rest.config.jaxrs;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

import org.springframework.core.env.Environment;

/**
 * Filter to remove configurable prefix from REST endpoints. Applied only to JAX-RS resources.
 */
@Provider
@PreMatching
public class PathPrefixFilter implements ContainerRequestFilter {

  private final String pathPrefix;

  @Inject
  public PathPrefixFilter(final Environment env) {
    pathPrefix = env.getRequiredProperty("nflow.rest.path.prefix");
  }

  @Override
  public void filter(ContainerRequestContext reqContext) throws IOException {
    if (reqContext.getUriInfo().getPath(false).startsWith(pathPrefix)) {
      URI baseUri = reqContext.getUriInfo().getBaseUri();
      String fullPathPrefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
      try {
        URI pathUri = new URI(reqContext.getUriInfo().getPath(true).substring(fullPathPrefix.length()));
        reqContext.setRequestUri(baseUri, pathUri);
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
