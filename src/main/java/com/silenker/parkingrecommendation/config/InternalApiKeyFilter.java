package com.silenker.parkingrecommendation.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部 API 鉴权过滤器
 * /api/internal/* 仅供 Python Agent / MCP Server 服务端调用，
 * 要求请求头携带 X-Internal-Key，与配置的 internal.api-key 一致。
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    @Value("${internal.api-key:parking-internal-key-2024}")
    private String apiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/internal/")) {
            String providedKey = request.getHeader("X-Internal-Key");
            if (!apiKey.equals(providedKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"status\":\"error\",\"error\":\"未授权的内部API调用\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
