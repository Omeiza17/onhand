# Frontend image: built PWA served by unprivileged nginx (listens on 8080, non-root).
# OUTLINE — verify base image tags against current releases before first use.
# Build from frontend/:  docker build -t onhand-web:dev .

FROM node:lts-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx/nginx-unprivileged:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
