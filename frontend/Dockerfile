FROM node:26-bullseye AS build

ENV NPM_CONFIG_STRICT_ALLOW_SCRIPTS=false
ENV NPM_CONFIG_ALLOW_SCRIPTS=true

RUN npm install -g npm@10

WORKDIR /app

COPY package*.json ./
#RUN npm ci
RUN npm ci

COPY . .
RUN npm run build

# --- Production image ---
FROM node:26-bullseye

WORKDIR /app

RUN npm install -g pm2

COPY --from=build /app/.output ./.output

EXPOSE 3000

CMD ["pm2-runtime", ".output/server/index.mjs"]
