FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
# npm install plutôt que npm ci : le lock généré sur macOS diffère sous Linux (dépendances optionnelles).
RUN npm install --no-audit --no-fund
COPY . .
# Configuration « development » : l'API est sur http://localhost:8080.
RUN npx ng build --configuration development

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/app-chat/browser /usr/share/nginx/html
EXPOSE 80
