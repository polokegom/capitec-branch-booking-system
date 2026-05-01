FROM node:20-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY . .
RUN npx ng build --configuration=production

FROM nginx:1.27-alpine AS runtime

RUN rm -f /etc/nginx/conf.d/default.conf

COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template

COPY --from=build /app/dist/capitec-booking/browser/ /usr/share/nginx/html/

ENV NGINX_PORT=8080 \
    BACKEND_UPSTREAM=http://backend:8080 \
    FUSIONAUTH_UPSTREAM=http://fusionauth:9011

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${NGINX_PORT}/healthz || exit 1
