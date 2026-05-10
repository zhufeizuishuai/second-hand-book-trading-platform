package org.example.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {


        // 获取请求的路径
        String requestURI = request.getRequestURI();

        // 对 WebSocket 握手请求直接放行，不进行 JWT 头校验
        // 因为 WebSocket 的 token 通过 URL 参数传递，并在 ChatWebSocketServer 中自行验证
        if (requestURI != null && requestURI.startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            System.out.println("Request URL: " + request.getRequestURL());
            System.out.println("Request Method: " + request.getMethod());

            String jwt = parseJwt(request);
            System.out.println("JWT Token: " + (jwt != null ? jwt.substring(0, Math.min(20, jwt.length())) + "..." : "null"));

            if (jwt != null) {
                boolean isValid = jwtUtils.validateJwtToken(jwt);
                System.out.println("JWT Valid: " + isValid);

                if (isValid) {
                    String username = jwtUtils.getUsernameFromJwtToken(jwt);
                    System.out.println("Username from JWT: " + username);

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    System.out.println("Authentication set successfully");
                } else {
                    System.out.println("JWT token is invalid");
                }
            } else {
                System.out.println("No JWT token found in request");
            }
        } catch (Exception e) {
            System.err.println("Cannot set user authentication: " + e.getMessage());
            e.printStackTrace();
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}