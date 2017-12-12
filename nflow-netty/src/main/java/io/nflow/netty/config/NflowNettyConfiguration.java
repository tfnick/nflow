package io.nflow.netty.config;

import javax.sql.DataSource;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.nflow.engine.config.NFlow;
import io.nflow.rest.config.RestConfiguration;
import reactor.core.publisher.Mono;

@Configuration
@ComponentScan("io.nflow.rest.v1.springweb")
@Import(RestConfiguration.class)
@EnableTransactionManagement
@EnableWebFlux
public class NflowNettyConfiguration implements WebFluxConfigurer, WebFilter {

  @Bean
  public PlatformTransactionManager transactionManager(@NFlow DataSource nflowDataSource) {
    return new DataSourceTransactionManager(nflowDataSource);
  }

  @Bean
  public DispatcherHandler webHandler(ApplicationContext context) {
    return new DispatcherHandler(context);
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/nflow/ui/**").addResourceLocations("classpath:/static/");
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    if (path.equals("/nflow/ui") || path.equals("/nflow/ui/explorer")) {
      ServerHttpResponse redirectResponse = exchange.getResponse();
      redirectResponse.setStatusCode(HttpStatus.FOUND);
      redirectResponse.getHeaders().add(HttpHeaders.LOCATION, path + "/index.html");
      redirectResponse.setComplete();
      return chain
          .filter(exchange.mutate().response(redirectResponse).build());
    }
    return chain.filter(exchange);
  }

}
