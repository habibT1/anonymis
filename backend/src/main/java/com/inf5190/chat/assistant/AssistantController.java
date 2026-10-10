package com.inf5190.chat.assistant;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.inf5190.chat.assistant.model.AskRequest;
import com.inf5190.chat.assistant.model.AskResponse;
import com.inf5190.chat.auth.AuthController;
import com.inf5190.chat.auth.session.CurrentUser;
import com.inf5190.chat.llm.LlmProvider;

@RestController
public class AssistantController {
    public static final String ASK_PATH = "/assistant/ask";
    public static final String INFO_PATH = "/assistant/info";

    private final AssistantService assistantService;
    private final CurrentUser currentUser;
    private final LlmProvider llm;

    public AssistantController(AssistantService assistantService, CurrentUser currentUser, LlmProvider llm) {
        this.assistantService = assistantService;
        this.currentUser = currentUser;
        this.llm = llm;
    }

    /** Quelle IA est utilisée (affiché dans l'interface). */
    @GetMapping(value = INFO_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> info() {
        return Map.of("model", llm.modelName(), "local", llm.isLocal());
    }

    @PostMapping(value = ASK_PATH, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AskResponse ask(
            @CookieValue(value = AuthController.SESSION_ID_COOKIE_NAME, required = false) String sessionId,
            @RequestBody AskRequest request) {
        return assistantService.ask(currentUser.username(sessionId), request);
    }
}
