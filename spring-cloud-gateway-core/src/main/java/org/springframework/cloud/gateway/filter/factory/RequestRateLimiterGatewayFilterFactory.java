/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

/**
 * User Request Rate Limiter filter. See https://stripe.com/blog/rate-limiters and
 * RequestRateLimiterGatewayFilter 使用 Redis + Lua 实现分布式限流。而限流的粒度，
 * 例如 URL / 用户 / IP 等，通过 org.springframework.cloud.gateway.filter.ratelimit.KeyResolver 实现类决定
 */
@ConfigurationProperties("spring.cloud.gateway.filter.request-rate-limiter")
public class RequestRateLimiterGatewayFilterFactory extends AbstractGatewayFilterFactory<RequestRateLimiterGatewayFilterFactory.Config> {

	public static final String KEY_RESOLVER_KEY = "keyResolver";
	private static final String EMPTY_KEY = "____EMPTY_KEY__";

	// 默认情况下，使用 RedisRateLimiter
	private final RateLimiter defaultRateLimiter;
	private final KeyResolver defaultKeyResolver;

	/** Switch to deny requests if the Key Resolver returns an empty key, defaults to true. */
	private boolean denyEmptyKey = true;

	/** HttpStatus to return when denyEmptyKey is true, defaults to FORBIDDEN. */
	private String emptyKeyStatusCode = HttpStatus.FORBIDDEN.name();

	public RequestRateLimiterGatewayFilterFactory(RateLimiter defaultRateLimiter,
												  KeyResolver defaultKeyResolver) {
		super(Config.class);
		this.defaultRateLimiter = defaultRateLimiter;
		this.defaultKeyResolver = defaultKeyResolver;
	}

	public KeyResolver getDefaultKeyResolver() {
		return defaultKeyResolver;
	}

	public RateLimiter getDefaultRateLimiter() {
		return defaultRateLimiter;
	}

	public boolean isDenyEmptyKey() {
		return denyEmptyKey;
	}

	public void setDenyEmptyKey(boolean denyEmptyKey) {
		this.denyEmptyKey = denyEmptyKey;
	}

	public String getEmptyKeyStatusCode() {
		return emptyKeyStatusCode;
	}

	public void setEmptyKeyStatusCode(String emptyKeyStatusCode) {
		this.emptyKeyStatusCode = emptyKeyStatusCode;
	}

	@SuppressWarnings("unchecked")
	@Override
	public GatewayFilter apply(Config config) {
		// 获得 KeyResolver：默认情况下，使用 PrincipalNameKeyResolver
		KeyResolver resolver = getOrDefault(config.keyResolver, defaultKeyResolver);
		// 获得 限流限制器：默认情况下，使用 RedisRateLimiter
		RateLimiter<Object> limiter = getOrDefault(config.rateLimiter, defaultRateLimiter);
		// 如果 Key Resolver 返回一个空键，则切换到拒绝请求，默认为 true
		boolean denyEmpty = getOrDefault(config.denyEmptyKey, this.denyEmptyKey);
		// 当 denyEmpty Key 为真时返回的HttpStatus，默认为 FORBIDDEN
		HttpStatusHolder emptyKeyStatus = HttpStatusHolder.parse(getOrDefault(config.emptyKeyStatus, this.emptyKeyStatusCode));

		return (exchange, chain) -> {
			Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);

			// resolve：获得请求的限流键
			return resolver.resolve(exchange).defaultIfEmpty(EMPTY_KEY).flatMap(key -> {
				// 当限流键为空时，过滤器链不会继续向下执行
				if (EMPTY_KEY.equals(key)) {
					if (denyEmpty) {
						setResponseStatus(exchange, emptyKeyStatus);
						return exchange.getResponse().setComplete();
					}
					return chain.filter(exchange);
				}
				// isAllowed 是否被限流
				return limiter.isAllowed(route.getId(), key).flatMap(response -> {

					for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {
						exchange.getResponse().getHeaders().add(header.getKey(), header.getValue());
					}

					// // 允许访问
					if (response.isAllowed()) {
						return chain.filter(exchange);
					}

					// 被限流，不允许访问
					setResponseStatus(exchange, config.getStatusCode());
					return exchange.getResponse().setComplete();
				});
			});
		};
	}

	private <T> T getOrDefault(T configValue, T defaultValue) {
		return (configValue != null) ? configValue : defaultValue;
	}

	public static class Config {
		private KeyResolver keyResolver;
		private RateLimiter rateLimiter;
		private HttpStatus statusCode = HttpStatus.TOO_MANY_REQUESTS;
		private Boolean denyEmptyKey;
		private String emptyKeyStatus;

		public KeyResolver getKeyResolver() {
			return keyResolver;
		}

		public Config setKeyResolver(KeyResolver keyResolver) {
			this.keyResolver = keyResolver;
			return this;
		}
		public RateLimiter getRateLimiter() {
			return rateLimiter;
		}

		public Config setRateLimiter(RateLimiter rateLimiter) {
			this.rateLimiter = rateLimiter;
			return this;
		}

		public HttpStatus getStatusCode() {
			return statusCode;
		}

		public Config setStatusCode(HttpStatus statusCode) {
			this.statusCode = statusCode;
			return this;
		}

		public Boolean getDenyEmptyKey() {
			return denyEmptyKey;
		}

		public Config setDenyEmptyKey(Boolean denyEmptyKey) {
			this.denyEmptyKey = denyEmptyKey;
			return this;
		}

		public String getEmptyKeyStatus() {
			return emptyKeyStatus;
		}

		public Config setEmptyKeyStatus(String emptyKeyStatus) {
			this.emptyKeyStatus = emptyKeyStatus;
			return this;
		}
	}

}
