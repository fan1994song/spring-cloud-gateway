package org.springframework.cloud.gateway.filter.ratelimit;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public interface KeyResolver {
	// 获取请求的限流key
	Mono<String> resolve(ServerWebExchange exchange);
}
