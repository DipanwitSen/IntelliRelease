FROM node:22-alpine
WORKDIR /app
# Lockfile first so the dependency layer is cached independently of source edits.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
EXPOSE 4200
# `start` already binds 0.0.0.0 so the dev server is reachable outside the container.
CMD ["npm", "start"]
