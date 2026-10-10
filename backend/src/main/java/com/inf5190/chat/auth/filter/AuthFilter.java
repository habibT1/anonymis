package com.inf5190.chat.auth.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import com.inf5190.chat.auth.AuthController;
import com.inf5190.chat.auth.session.SessionData;
import com.inf5190.chat.auth.session.SessionManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Filtre qui intercepte les requêtes HTTP et valide si elle est autorisée.
 */
public class AuthFilter extends OncePerRequestFilter {
    private final SessionManager sessionManager;
    private final List<String> allowedOrigins;


    public AuthFilter(SessionManager sessionManager, List<String> allowedOrigins) {
        this.sessionManager = sessionManager;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws IOException, ServletException {

        if (request.getMethod().equalsIgnoreCase(HttpMethod.OPTIONS.name())) {
            chain.doFilter(request, response);
            return;
        }

        final Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            this.sendAuthErrorResponse(request, response);
            return;
        }

        final Optional<Cookie> sessionCookie =
                Arrays.stream(cookies)
                        .filter(c -> c.getName() != null
                                && c.getName().equals(AuthController.SESSION_ID_COOKIE_NAME))
                        .findFirst();
        if (sessionCookie.isEmpty()) {
            this.sendAuthErrorResponse(request, response);
            return;
        }

        SessionData sessionData = this.sessionManager.getSession(sessionCookie.get().getValue());

        if (sessionData == null) {
            this.sendAuthErrorResponse(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    protected void sendAuthErrorResponse(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Cookie sessionIdCookie = new Cookie(AuthController.SESSION_ID_COOKIE_NAME, null);
        sessionIdCookie.setPath("/");
        sessionIdCookie.setSecure(true);
        sessionIdCookie.setHttpOnly(true);
        sessionIdCookie.setMaxAge(0);

        response.addCookie(sessionIdCookie);

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (this.allowedOrigins.contains(origin)) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }


        if (request.getRequestURI().contains(AuthController.AUTH_LOGOUT_PATH)) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
