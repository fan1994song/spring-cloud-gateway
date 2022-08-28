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

package org.springframework.cloud.gateway.filter;

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class WebClientHttpRoutingFilter implements GlobalFilter, Ordered {

	private final WebClient webClient;

	public WebClientHttpRoutingFilter(WebClient webClient) {
		this.webClient = webClient;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		// 获得 requestUrl
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		// 是否可以处理
		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || (!"http".equals(scheme) && !"https".equals(scheme))) {
			return chain.filter(exchange);
		}
		// 设置处理状态值
		setAlreadyRouted(exchange);

		ServerHttpRequest request = exchange.getRequest();

		HttpMethod method = request.getMethod();

		// 创建向后端服务的请求
		RequestBodySpec bodySpec = this.webClient.method(method)
				.uri(requestUrl)
				.headers(httpHeaders -> {
					httpHeaders.addAll(request.getHeaders());
					//TODO: can this support preserviceHostHeader?
					httpHeaders.remove(HttpHeaders.HOST);
				});

		RequestHeadersSpec<?> headersSpec;
		if (requiresBody(method)) {
			headersSpec = bodySpec.body(BodyInserters.fromDataBuffers(request.getBody()));
		} else {
			headersSpec = bodySpec;
		}

		// 发起向后端服务的请求
		return headersSpec.exchange()
				// .log("webClient route")
				.flatMap(res -> {
					ServerHttpResponse response = exchange.getResponse();
					response.getHeaders().putAll(res.headers().asHttpHeaders());
					response.setStatusCode(res.statusCode());
					// Defer committing the response until all route filters have run
					// Put client response as ServerWebExchange attribute and write response later NettyWriteResponseFilter
					// 推迟提交响应，直到所有路由过滤器都运行 将客户端响应作为 ServerWebExchange 属性并稍后写入响应 NettyWriteResponseFilter
					// 设置 Response 到 CLIENT_RESPONSE_ATTR
					exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, res);
					return chain.filter(exchange);
				});
	}

	private boolean requiresBody(HttpMethod method) {
		switch (method) {
			case PUT:
			case POST:
			case PATCH:
				return true;
			default:
				return false;
		}
	}
}
