package com.inf5190.chat.messages;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.HttpCookie;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.client.TestRestTemplate.HttpClientOption;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.inf5190.chat.auth.model.LoginRequest;
import com.inf5190.chat.auth.model.LoginResponse;
import com.inf5190.chat.messages.model.Message;
import com.inf5190.chat.messages.model.NewMessageRequest;
import com.inf5190.chat.messages.repository.FirestoreMessage;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@PropertySource("classpath:firebase.properties")
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".*")
public class IntTestMessageController {
    private final FirestoreMessage message1 =
            new FirestoreMessage("u1", Timestamp.now(), "t1", null);
    private final FirestoreMessage message2 =
            new FirestoreMessage("u2", Timestamp.now(), "t2", null);

    @Value("${firebase.project.id}")
    private String firebaseProjectId;

    @Value("${firebase.emulator.port}")
    private String emulatorPort;

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate;

    @Autowired
    private Firestore firestore;

    private String messagesEndpointUrl;
    private String loginEndpointUrl;

    @BeforeEach
    public void setup() throws InterruptedException, ExecutionException {
        this.restTemplate = new TestRestTemplate(HttpClientOption.ENABLE_COOKIES);
        this.messagesEndpointUrl = "http://localhost:" + port + "/messages";
        this.loginEndpointUrl = "http://localhost:" + port + "/auth/login";

        // Pour ajouter deux messages dans firestore au début de chaque test.
        this.firestore.collection("messages").document("1").create(this.message1).get();
        this.firestore.collection("messages").document("2").create(this.message2).get();
    }

    @AfterEach
    public void testDown() {
        // Pour effacer le contenu de l'émulateur entre chaque test.
        this.restTemplate.delete("http://localhost:" + this.emulatorPort + "/emulator/v1/projects/"
                + this.firebaseProjectId + "/databases/(default)/documents");
    }

    /**
     * Test 1: GET /messages sans authentification
     * Devrait retourner HTTP 403 Forbidden
     */
    @Test
    public void testGetMessagesWithoutAuth() {
        ResponseEntity<String> response =
                this.restTemplate.getForEntity(this.messagesEndpointUrl, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    /**
     * Test 2: POST /messages sans authentification
     * Devrait retourner HTTP 403 Forbidden
     */
    @Test
    public void testPostMessageWithoutAuth() {
        NewMessageRequest request = new NewMessageRequest("testuser", "test message", null);

        ResponseEntity<String> response =
                this.restTemplate.postForEntity(this.messagesEndpointUrl, request, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    /**
     * Test 3: GET /messages avec authentification, sans fromId
     * Devrait retourner HTTP 200 et un tableau de messages (limité à 20)
     * Vérifie aussi l'ordre des messages (du plus ancien au plus récent)
     */
    @Test
    public void testGetMessagesWithoutFromId() {
        final String sessionCookie = this.login();

        final HttpHeaders header = this.createHeadersWithSessionCookie(sessionCookie);
        final HttpEntity<Object> headers = new HttpEntity<Object>(header);
        final ResponseEntity<Message[]> response = this.restTemplate
                .exchange(this.messagesEndpointUrl, HttpMethod.GET, headers, Message[].class);

        // Vérifier le code de retour HTTP
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        // Vérifier qu'on a bien reçu des messages
        Message[] messages = response.getBody();
        assertThat(messages).isNotNull();
        assertThat(messages.length).isEqualTo(2);

        // Vérifier l'ordre des messages (du plus ancien au plus récent)
        assertThat(messages[0].username()).isEqualTo("u1");
        assertThat(messages[0].text()).isEqualTo("t1");
        assertThat(messages[1].username()).isEqualTo("u2");
        assertThat(messages[1].text()).isEqualTo("t2");

        // Vérifier que les timestamps sont dans l'ordre croissant
        assertThat(messages[0].timestamp()).isLessThanOrEqualTo(messages[1].timestamp());
    }

    /**
     * Test 4: GET /messages avec authentification et fromId valide
     * Devrait retourner HTTP 200 et les messages après le message spécifié
     */
    @Test
    public void testGetMessagesWithFromId() throws InterruptedException, ExecutionException {
        final String sessionCookie = this.login();

        // Ajouter un troisième message pour le test
        FirestoreMessage message3 = new FirestoreMessage("u3", Timestamp.now(), "t3", null);
        this.firestore.collection("messages").document("3").create(message3).get();

        // Récupérer les messages à partir du message "1"
        String urlWithFromId = this.messagesEndpointUrl + "?fromId=1";
        final HttpHeaders header = this.createHeadersWithSessionCookie(sessionCookie);
        final HttpEntity<Object> headers = new HttpEntity<Object>(header);
        final ResponseEntity<Message[]> response = this.restTemplate
                .exchange(urlWithFromId, HttpMethod.GET, headers, Message[].class);

        // Vérifier le code de retour HTTP
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        // Vérifier qu'on a bien reçu les messages après le fromId
        Message[] messages = response.getBody();
        assertThat(messages).isNotNull();

        // On devrait avoir les messages 2 et 3 (mais pas le message 1)
        assertThat(messages.length).isGreaterThanOrEqualTo(2);

        // Vérifier que le message 1 n'est pas dans les résultats
        boolean hasMessage1 = false;
        for (Message msg : messages) {
            if (msg.id().equals("1")) {
                hasMessage1 = true;
                break;
            }
        }
        assertThat(hasMessage1).isFalse();

        // Vérifier l'ordre (croissant par timestamp)
        for (int i = 0; i < messages.length - 1; i++) {
            assertThat(messages[i].timestamp())
                .isLessThanOrEqualTo(messages[i + 1].timestamp());
        }
    }

    /**
     * Test 5: GET /messages avec plus de 20 messages
     * Devrait retourner HTTP 200 et exactement 20 messages (limite)
     */
    @Test
    public void testGetMessagesWithMoreThan20Messages() throws InterruptedException, ExecutionException {
        final String sessionCookie = this.login();

        // Ajouter 23 messages supplémentaires (total = 25 avec les 2 déjà créés)
        for (int i = 3; i <= 25; i++) {
            FirestoreMessage msg = new FirestoreMessage(
                "user" + i,
                Timestamp.now(),
                "text" + i,
                null
            );
            this.firestore.collection("messages").document(String.valueOf(i)).create(msg).get();
            // Petit délai pour garantir l'ordre des timestamps
            Thread.sleep(10);
        }

        final HttpHeaders header = this.createHeadersWithSessionCookie(sessionCookie);
        final HttpEntity<Object> headers = new HttpEntity<Object>(header);
        final ResponseEntity<Message[]> response = this.restTemplate
                .exchange(this.messagesEndpointUrl, HttpMethod.GET, headers, Message[].class);

        // Vérifier le code de retour HTTP
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        // Vérifier qu'on a exactement 20 messages (limite)
        Message[] messages = response.getBody();
        assertThat(messages).isNotNull();
        assertThat(messages.length).isEqualTo(20);

        // Vérifier l'ordre des messages
        for (int i = 0; i < messages.length - 1; i++) {
            assertThat(messages[i].timestamp())
                .isLessThanOrEqualTo(messages[i + 1].timestamp());
        }
    }

    /**
     * Test 6: GET /messages avec fromId invalide
     * Devrait retourner HTTP 404 Not Found
     */
    @Test
    public void testGetMessagesWithInvalidFromId() {
        final String sessionCookie = this.login();

        // Utiliser un fromId qui n'existe pas
        String urlWithInvalidFromId = this.messagesEndpointUrl + "?fromId=invalid-id-999";
        final HttpHeaders header = this.createHeadersWithSessionCookie(sessionCookie);
        final HttpEntity<Object> headers = new HttpEntity<Object>(header);
        final ResponseEntity<String> response = this.restTemplate
                .exchange(urlWithInvalidFromId, HttpMethod.GET, headers, String.class);

        // Vérifier le code de retour HTTP 404
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }


    /**
     * Se connecte et retourne le cookie de session.
     *
     * @return le cookie de session.
     */
    private String login() {
        ResponseEntity<LoginResponse> response =
                this.restTemplate.postForEntity(this.loginEndpointUrl,
                        new LoginRequest("username", "password"), LoginResponse.class);

        String setCookieHeader = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        HttpCookie sessionCookie = HttpCookie.parse(setCookieHeader).get(0);
        return sessionCookie.getName() + "=" + sessionCookie.getValue();
    }

    private HttpEntity<NewMessageRequest> createRequestEntityWithSessionCookie(
            NewMessageRequest messageRequest, String cookieValue) {
        HttpHeaders header = this.createHeadersWithSessionCookie(cookieValue);
        return new HttpEntity<NewMessageRequest>(messageRequest, header);
    }

    private HttpHeaders createHeadersWithSessionCookie(String cookieValue) {
        HttpHeaders header = new HttpHeaders();
        header.add(HttpHeaders.COOKIE, cookieValue);
        return header;
    }
}
