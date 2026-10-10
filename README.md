# Anonymis

**Posez vos questions à une IA sur vos PDF, sans jamais lui montrer vos données personnelles.**

Anonymis est un proxy de confidentialité entre vous et un modèle d'IA (Gemma 4 de Google, en ligne ou en local via Ollama). Avant tout appel, les noms, e-mails, adresses, NAS, IBAN et autres données sensibles sont remplacés **localement** par des jetons neutres. L'IA travaille sur le texte anonymisé, puis Anonymis remet les vraies valeurs dans la réponse.

```
Vous :        « Qui doit donner le préavis dans ce contrat ? »
L'IA reçoit : « … [PERSONNE_1] doit donner un préavis de 2 semaines à [ORGANISATION_1] … »
L'IA répond : « [PERSONNE_1] doit prévenir [ORGANISATION_1] 2 semaines à l'avance. »
Vous lisez :  « Jean Dupont doit prévenir Acme inc. 2 semaines à l'avance. »
```

> **Statut : MVP fonctionnel (hackathon).** Import de PDF → anonymisation locale → Gemma 4 (en ligne ou en local) → réponse avec les vrais noms. Ce qui reste est dans [PLAN.md](PLAN.md).

---

## Fonctionnalités

| Fonctionnalité | Statut |
|---|---|
| Upload de PDF et extraction du texte | fait |
| Détection des données personnelles en FR/EN : noms, e-mails, téléphones, adresses, NAS, NIR, IBAN, cartes bancaires, dates de naissance | fait |
| Détection des **secrets** : clés d'API, jetons, mots de passe, valeurs de `.env`, clés privées, chaînes de connexion | fait |
| **Noms sensibles** (clients, noms de code de projets) + liste de termes à ne jamais masquer | fait |
| Questions/réponses et résumés sur vos documents | fait |
| **Vue IA** : voir exactement ce que l'IA a reçu (+ aperçu avant envoi) | fait |
| **LeakGuard** : bloque tout appel qui contiendrait encore une donnée réelle | fait |
| Export d'un PDF caviardé (texte réellement supprimé, mêmes jetons que la conversation) | fait |
| Export de la réponse en PDF, avec les vrais noms | fait |
| Fusion, découpe et extraction de pages, en local et sans IA | prévu (P1) |
| OCR des PDF scannés | prévu (P2) |
| **Gemma 4** (Google) en ligne via l'API Gemini, par défaut | fait |
| Mode 100 % local : Gemma 4 via Ollama (`LLM_PROVIDER=ollama`) | fait |
| Connexion utilisateur (JWT) | fait existant |
| Mises à jour temps réel (WebSocket) | fait existant |

## Comment ça marche

```
 PDF ──► Extraction ──► Détection PII ──► Tokenisation ──► LeakGuard ──► IA
                                              │                          │
                                       Table de correspondance           │
                                     (reste sur le serveur, chiffrée)    │
                                              │                          ▼
 Réponse ◄──────────────────────────── Restauration ◄────────── Réponse avec jetons
```

1. **Extraction** : texte et positions des mots (PyMuPDF).
2. **Détection**, en trois familles :
   - *données personnelles* : Presidio + spaCy `fr_core_news_lg` + regex (NAS avec contrôle Luhn, IBAN, codes postaux canadiens…) ;
   - *secrets* : formats de clés connus (`sk-…`, `AKIA…`, `ghp_…`…, règles gitleaks), lignes `KEY=valeur`, chaînes à forte entropie. Le nom de la variable est gardé, la valeur est masquée : `OPENAI_API_KEY=[CLE_API_1]` ;
   - *noms métier* : votre liste de clients et de noms de code de projets.
3. **Tokenisation** : chaque entité devient un jeton stable dans la conversation (`[PERSONNE_1]`, `[EMAIL_2]`…). La table de correspondance ne quitte jamais le serveur.
4. **LeakGuard** : dernière vérification avant l'envoi. Si une valeur réelle est encore présente, l'appel est refusé.
5. **IA** : seul du **texte anonymisé** est envoyé, jamais le PDF original.
6. **Restauration** : les jetons sont remplacés par les vraies valeurs avant l'affichage ou l'export.

## Architecture

| Composant | Techno | Rôle |
|---|---|---|
| `frontend/` | Angular 20 | Documents, chat avec l'assistant, Vue IA |
| `backend/` | Spring Boot 3, Java 21 | Auth, documents, orchestration, `TokenVault`, `LeakGuard`, fournisseurs d'IA |
| `privacy-engine/` | Python 3.12, FastAPI | Extraction PDF, détection PII, caviardage, génération de PDF |
| Stockage | Firestore + Firebase Storage (émulateur en dev) | Comptes, conversations, PDF **chiffrés** (AES-256-GCM) |
| IA | **Gemma 4** (Google) en ligne via l'API Gemini (défaut), ou en local via Ollama | Raisonnement sur le texte anonymisé |

Détails, schéma complet et API : [PLAN.md §3](PLAN.md#3-architecture-cible).

## Démarrage rapide avec Docker (le plus simple)

```bash
cp .env.example .env        # puis remplir GEMINI_API_KEY et JWT_SECRET_KEY_BASE64 (voir « Configurer les clés »)
# placer la clé de service Firebase dans backend/firebase-key.json
docker compose up --build   # http://localhost:4200
```

Le privacy-engine n'est joignable que par le backend, sur le réseau interne de Docker : il n'est pas exposé sur la machine. Démo : [demo/DEMO.md](demo/DEMO.md), avec un PDF fictif dans `demo/`.

## Démarrage rapide (développement)

Prérequis : Java 21, Node 20+, Python 3.12+, une clé `GEMINI_API_KEY` (voir ci-dessous). Optionnel : [Ollama](https://ollama.com) pour faire tourner Gemma 4 en local (`ollama pull gemma4:e4b`, ~6,6 Go, puis `LLM_PROVIDER=ollama`).

```bash
# 1. Émulateur Firestore (port 8181, UI sur :4000)
cd backend && firebase emulators:start

# 2. Backend : http://localhost:8080
cd backend
export JWT_SECRET_KEY_BASE64="$(openssl rand -base64 32)"
export GEMINI_API_KEY="AIza..."                # pour l'assistant (Gemma 4 en ligne)
./gradlew bootRun

# 3. privacy-engine : http://127.0.0.1:8090
cd privacy-engine
pip install -r requirements.txt && python -m spacy download fr_core_news_lg
uvicorn app:app --host 127.0.0.1 --port 8090

# 4. Frontend : http://localhost:4200
cd frontend && npm install && npm start
```

### Tester l'API sans interface

```bash
# Connexion (crée le compte s'il n'existe pas)
curl -c jar.txt -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}'

# Importer un PDF → renvoie son id
curl -b jar.txt -F "file=@contrat.pdf" http://localhost:8080/documents

# Voir ce que l'IA recevrait, SANS l'appeler (pas besoin de clé d'API)
curl -b jar.txt -X POST http://localhost:8080/assistant/ask -H 'Content-Type: application/json' \
  -d '{"question":"Qui doit donner le préavis ?","documentIds":["<id>"],"dryRun":true}'

# Vraie question (nécessite GEMINI_API_KEY, ou Gemma 4 en local) ; réutiliser conversationId pour les questions suivantes
curl -b jar.txt -X POST http://localhost:8080/assistant/ask -H 'Content-Type: application/json' \
  -d '{"question":"Qui doit donner le préavis ?","documentIds":["<id>"]}'
```

Réponses d'erreur : `422 leak_blocked` (LeakGuard a bloqué l'envoi), `503 privacy_engine_unavailable` (rien n'a été envoyé), `502 llm_unavailable` (clé absente ou erreur de l'API).

### Configurer les clés (à faire une fois)

Toutes les clés vont dans un fichier **`.env` à la racine du projet**. Ce fichier est ignoré par git : il ne doit **jamais** être commité ni collé dans un chat, un ticket ou une capture d'écran.

```bash
cp .env.example .env      # puis ouvrir .env dans un éditeur
```

| Clé | Comment l'obtenir | Quoi coller dans `.env` |
|---|---|---|
| `GEMINI_API_KEY` *(Gemma 4 en ligne, fournisseur par défaut)* | 1. Aller sur **https://aistudio.google.com/apikey** et se connecter avec un compte Google<br>2. **Create API key** (choisir ou créer un projet Google Cloud)<br>3. Copier la clé (elle commence par `AIza…`) | `GEMINI_API_KEY=AIza…` (la clé entière, sans guillemets ni espaces) |
| `JWT_SECRET_KEY_BASE64` | Générer : `openssl rand -base64 32` | `JWT_SECRET_KEY_BASE64=<résultat de la commande>` |
| `DOCUMENT_ENCRYPTION_KEY` | Générer : `openssl rand -base64 32` | `DOCUMENT_ENCRYPTION_KEY=<résultat de la commande>` |
| `firebase-key.json` *(fichier, pas une variable)* | Console Firebase → **Paramètres du projet → Comptes de service → Générer une nouvelle clé privée** | Placer le fichier téléchargé dans `backend/firebase-key.json` (déjà ignoré par git) |

Charger le `.env` avant de lancer le backend :

```bash
set -a; source .env; set +a
cd backend && ./gradlew bootRun
```

Vérifier que la clé Gemini fonctionne, **sans l'afficher** :

```bash
set -a; source .env; set +a
curl -s "https://generativelanguage.googleapis.com/v1beta/models/gemma-4-26b-a4b-it" \
  -H "x-goog-api-key: $GEMINI_API_KEY" -o /dev/null -w "%{http_code}\n"   # 200 = OK
```

**Si une clé a fuité** (commit, capture, message…), elle doit être **révoquée** et remplacée tout de suite : AI Studio → API keys → supprimer, ou Console Firebase → Comptes de service → supprimer la clé. Supprimer le commit ne suffit pas.

Pour une équipe de hackathon : chacun crée **sa propre** clé Gemini. On ne partage pas une clé par message.

### Variables d'environnement

| Variable | Où | Description |
|---|---|---|
| `JWT_SECRET_KEY_BASE64` | backend | Clé de signature des JWT (**obligatoire**, pas de valeur par défaut en prod) |
| `LLM_PROVIDER` | backend | `gemini` (Gemma 4 en ligne, défaut) · `ollama` (Gemma 4 en local, rien ne sort) |
| `GEMINI_API_KEY` | backend | Clé Google AI Studio |
| `GEMINI_MODEL` | backend | `gemma-4-26b-a4b-it` (défaut, rapide) ou `gemma-4-31b-it` (plus précis) |
| `GEMINI_THINKING_LEVEL` | backend | `minimal` (défaut, rapide) ou `high` (réfléchit avant de répondre) |
| `OLLAMA_URL` | backend | défaut `http://localhost:11434` |
| `OLLAMA_MODEL` | backend | défaut `gemma4:e4b` ; `gemma4:12b` / `gemma4:26b` si la machine a plus de RAM |
| `OLLAMA_THINK` | backend | `false` par défaut ; `true` = Gemma réfléchit avant de répondre (meilleur, ~7x plus lent) |
| `OLLAMA_NUM_CTX` | backend | taille du contexte, défaut `32768` (sinon Ollama tronque les longs documents) |
| `PRIVACY_ENGINE_URL` | backend | défaut `http://127.0.0.1:8090` |
| `DOCUMENT_ENCRYPTION_KEY` | backend | Clé AES-256 (base64) pour chiffrer les PDF stockés et la table de correspondance |
| `ALLOWED_ORIGINS` | backend | Origines CORS autorisées |
| `GOOGLE_CLOUD_PROJECT` | backend | Projet Firebase |

## Modèle de sécurité

**Ce que Google / le fournisseur d'IA voit** : du texte où les identifiants directs sont remplacés par des jetons.
**Ce qu'il ne voit jamais** : le PDF original, la table de correspondance, les valeurs réelles.

**Limites assumées :**
- La détection automatique n'est pas parfaite. LeakGuard et la correction manuelle réduisent le risque, sans l'éliminer.
- Le contexte peut parfois suffire à ré-identifier quelqu'un (« le PDG d'Acme à Montréal »). Le masquage des organisations, lieux et dates est réglable.
- Pour un besoin « zéro donnée qui sort », utilisez le mode Ollama local.
- Avec Gemma 4 **en ligne**, le texte anonymisé est traité par Google. Les conditions de l'offre gratuite d'AI Studio peuvent autoriser Google à utiliser ces contenus : vérifiez-les avant un usage réel. C'est justement pour ça qu'Anonymis n'envoie jamais de données en clair.

> **Secrets** : ne jamais commiter `backend/firebase-key.json`, `backend/firebase-production-key.json` ni `backend/cookies.txt`. Voir la Phase 0 de [PLAN.md](PLAN.md#phase-0--assainissement--journée-à-faire-en-premier).

## Structure du dépôt

```
.
├── README.md            ← vous êtes ici
├── PLAN.md              ← feuille de route et décisions
├── docker-compose.yml   lance les 3 services
├── demo/                PDF fictif + scénario de démo
├── frontend/            Angular (app de chat existante → UI Anonymis)
├── backend/             Spring Boot (API existante + modules documents/privacy/llm/assistant)
└── privacy-engine/      service Python de détection des données sensibles et de lecture PDF
```

## Licence

Distribué sous licence [MIT](LICENSE).

## Équipe

Projet réalisé dans le cadre d'un hackathon, à partir du TP INF5190.
