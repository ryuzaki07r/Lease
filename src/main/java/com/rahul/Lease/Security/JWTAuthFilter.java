package com.rahul.Lease.Security;

import com.rahul.Lease.Exceptions.ResourceNotFoundException;
import com.rahul.Lease.Models.User;
import com.rahul.Lease.Services.Interfaces.UserService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JWTAuthFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final JWTService jwtService;

    @Autowired
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver handlerExceptionResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try{
            final String requestTokenHeader = request.getHeader("Authorization");
            if(requestTokenHeader == null || !requestTokenHeader.startsWith("Bearer ")){
                filterChain.doFilter(request, response);
                return;
            }
            String token = requestTokenHeader.substring(7);
            Long userId = jwtService.getUserIdFromToken(token, JWTService.ACCESS_TOKEN);
            if(userId != null && SecurityContextHolder.getContext().getAuthentication() == null){
                User user;
                try {
                    user = userService.getUserById(userId);
                } catch (ResourceNotFoundException ex) {
                    // Valid signature but the account no longer exists
                    throw new JwtException("User for this token no longer exists");
                }
                UsernamePasswordAuthenticationToken authenticationToken =
                         new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authenticationToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex){
            handlerExceptionResolver.resolveException(request,response,null, ex);
        }
    }
}
