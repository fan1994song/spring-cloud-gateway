package org.springframework.cloud.gateway.filter;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * Contract to allow a {@link WebFilter} to delegate to the next in the chain.
 *
 * 允许委托给链中的下一个,filter责任链模式去依次执行相关逻辑
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public interface GatewayFilterChain {

	/**
	 * Delegate to the next {@code WebFilter} in the chain.
	 * @param exchange the current server exchange
	 * @return {@code Mono<Void>} to indicate when request handling is complete
	 */
	Mono<Void> filter(ServerWebExchange exchange);

}
