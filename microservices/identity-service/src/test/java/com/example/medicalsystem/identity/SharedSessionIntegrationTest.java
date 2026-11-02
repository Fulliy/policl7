package com.example.medicalsystem.identity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import com.example.medicalsystem.identity.repository.UserAccountRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.net.CookieManager;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SharedSessionIntegrationTest {

    @Autowired SessionRepository sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @LocalServerPort int identityPort;
    private String sessionId;
    private String registeredUsername;

    @AfterEach
    void removeSession() {
        if (sessionId != null) sessions.deleteById(sessionId);
        if (registeredUsername != null) users.findByUsername(registeredUsername).ifPresent(users::delete);
    }

    @Test
    void jdbcSessionIsRecognizedByIdentityAppointmentsLegacyAndGateway() throws Exception {
        String username = jdbc.queryForObject("select username from users order by id_user limit 1", String.class);
        assertTrue(sessions.getClass().getName().contains("JdbcIndexedSessionRepository"),
                "Expected JDBC-backed session repository, got " + sessions.getClass().getName());
        Session session = sessions.createSession();
        sessionId = session.getId();
        var principal = User.withUsername(username).password("").authorities("PATIENT").build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        sessions.save(session);
        assertNotNull(sessions.findById(sessionId).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> identity = get(client, "http://localhost:" + identityPort + "/api/users/me");
        assertEquals(200, identity.statusCode());
        assertEquals(200, get(client, "http://localhost:8082/api/services").statusCode());
        assertEquals(200, get(client, "http://localhost:8084/api/home").statusCode());
        assertEquals(200, get(client, "http://localhost:8080/api/services").statusCode());
    }

    @Test
    void publicRegistrationReachesValidationWithoutDisablingOtherSecurity() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + identityPort + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode(), "Invalid registration must fail validation, not authentication or CSRF: "
                + response.body());
    }

    @Test
    void registrationLoginAndGatewayShareTheAuthenticatedSession() throws Exception {
        registeredUsername = "cloud-test-" + UUID.randomUUID();
        String password = "TempTestPassword-17";
        String email = registeredUsername + "@example.test";
        String body = "{\"username\":\"" + registeredUsername + "\",\"password\":\"" + password
                + "\",\"confirmPassword\":\"" + password
                + "\",\"surname\":\"Test\",\"firstName\":\"Patient\",\"email\":\"" + email + "\"}";
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest register = HttpRequest.newBuilder(URI.create("http://localhost:" + identityPort + "/api/auth/register"))
                .header("Content-Type", "application/json").POST(BodyPublishers.ofString(body)).build();
        assertEquals(201, client.send(register, HttpResponse.BodyHandlers.ofString()).statusCode());

        HttpResponse<String> loginPage = client.send(HttpRequest.newBuilder(URI.create("http://localhost:8080/login"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginPage.statusCode());
        Matcher csrf = Pattern.compile("(?s)<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                .matcher(loginPage.body());
        assertTrue(csrf.find(), "Expected Thymeleaf login form to include CSRF token");
        String form = "username=" + encode(registeredUsername) + "&password=" + encode(password)
                + "&_csrf=" + encode(csrf.group(1));
        HttpRequest login = HttpRequest.newBuilder(URI.create("http://localhost:8080/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(form)).build();
        assertEquals(302, client.send(login, HttpResponse.BodyHandlers.ofString()).statusCode());

        HttpResponse<String> profile = client.send(HttpRequest.newBuilder(URI.create("http://localhost:8080/api/users/me"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, profile.statusCode());
        assertTrue(profile.body().contains(registeredUsername));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> get(HttpClient client, String url) throws Exception {
        String encodedId = Base64.getUrlEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Cookie", "SESSION=" + encodedId)
                .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
