package com.inf5190.chat.assistant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.inf5190.chat.assistant.ConversationStore.Conversation;
import com.inf5190.chat.assistant.model.AskRequest;
import com.inf5190.chat.assistant.model.AskResponse;
import com.inf5190.chat.documents.DocumentStore;
import com.inf5190.chat.documents.DocumentStore.StoredDocument;
import com.inf5190.chat.llm.LlmProvider;
import com.inf5190.chat.llm.LlmProvider.LlmResponse;
import com.inf5190.chat.llm.LlmProvider.Turn;
import com.inf5190.chat.privacy.Anonymizer;
import com.inf5190.chat.privacy.LeakGuard;
import com.inf5190.chat.privacy.TokenVault;

/**
 * Le harness : extraire → anonymiser → vérifier (LeakGuard) → IA → restaurer.
 */
@Service
public class AssistantService {
    static final String SYSTEM_PROMPT = """
            Tu es Anonymis, un assistant qui analyse des documents pour l'utilisateur.

            Pour protéger la vie privée, les données sensibles des documents et des questions ont été \
            remplacées par des jetons de la forme [TYPE_N], par exemple [PERSONNE_1], [EMAIL_2], \
            [ORGANISATION_1] ou [CLE_API_1]. L'application remplacera ces jetons par les vraies valeurs \
            avant d'afficher ta réponse à l'utilisateur.

            Règles :
            - Recopie les jetons exactement tels quels, crochets compris, quand tu parles de ces éléments.
            - N'essaie pas de deviner les vraies valeurs et ne commente pas le fait qu'elles sont masquées.
            - N'invente jamais de nouveau jeton.
            - Un même jeton désigne toujours la même chose dans toute la conversation.
            - Réponds dans la langue de la question, de façon claire et concise. Cite l'article ou la section \
            du document quand c'est utile.
            - Écris en texte simple, sans Markdown (pas de **, pas de #). Pour une liste, commence les lignes par « - ».
            """;

    private final ConversationStore conversations;
    private final DocumentStore documents;
    private final Anonymizer anonymizer;
    private final LeakGuard leakGuard;
    private final TokenVault tokenVault;
    private final LlmProvider llm;

    public AssistantService(ConversationStore conversations, DocumentStore documents, Anonymizer anonymizer,
            LeakGuard leakGuard, TokenVault tokenVault, LlmProvider llm) {
        this.conversations = conversations;
        this.documents = documents;
        this.anonymizer = anonymizer;
        this.leakGuard = leakGuard;
        this.tokenVault = tokenVault;
        this.llm = llm;
    }

    public AskResponse ask(String owner, AskRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question vide");
        }
        Conversation conv = request.conversationId() == null
                ? conversations.create(owner, request.options())
                : conversations.find(owner, request.conversationId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation introuvable"));

        synchronized (conv) {
            Map<String, Integer> masked = new HashMap<>();
            StringBuilder message = new StringBuilder();

            // 1. Documents pas encore envoyés dans cette conversation. Le nom du fichier n'est pas envoyé :
            //    il contient souvent un nom de personne (« CV_Sophie_Lavoie.pdf »).
            List<String> newDocs = new ArrayList<>();
            for (String docId : request.documentIds() == null ? List.<String>of() : request.documentIds()) {
                if (conv.includedDocuments.contains(docId)) {
                    continue;
                }
                StoredDocument doc = documents.find(owner, docId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));
                Anonymizer.Result r = anonymizer.anonymize(conv.id(), doc.text(), conv.options());
                r.maskedByType().forEach((k, v) -> masked.merge(k, v, Integer::sum));
                int n = conv.includedDocuments.size() + newDocs.size() + 1;
                message.append("<document numero=\"").append(n).append("\">\n")
                        .append(r.text()).append("\n</document>\n\n");
                newDocs.add(docId);
            }

            // 2. La question elle-même : l'utilisateur peut y taper des données sensibles.
            Anonymizer.Result q = anonymizer.anonymize(conv.id(), request.question().strip(), conv.options());
            q.maskedByType().forEach((k, v) -> masked.merge(k, v, Integer::sum));
            message.append(q.text());
            String sent = message.toString();

            // 3. LeakGuard sur tout ce qui va partir : historique + nouveau message.
            List<Turn> turns = new ArrayList<>(conv.turns);
            turns.add(Turn.user(sent));
            StringBuilder payload = new StringBuilder();
            turns.forEach(t -> payload.append(t.text()).append('\n'));
            leakGuard.check(conv.id(), payload.toString(), conv.options());
            int total = masked.values().stream().mapToInt(Integer::intValue).sum();

            if (request.dryRun()) {
                return new AskResponse(conv.id(), null, new AskResponse.AiView(sent, null),
                        masked, total, List.of(), null);
            }

            // 4. Appel à l'IA, puis restauration des vraies valeurs.
            LlmResponse response = llm.complete(SYSTEM_PROMPT, turns);
            TokenVault.Restored restored = tokenVault.detokenize(conv.id(), response.text(), false);

            conv.turns.add(Turn.user(sent));
            conv.turns.add(Turn.assistant(response.text()));
            conv.includedDocuments.addAll(newDocs);

            return new AskResponse(conv.id(), restored.text(), new AskResponse.AiView(sent, response.text()),
                    masked, total, restored.unknownTokens(), response.model());
        }
    }
}
