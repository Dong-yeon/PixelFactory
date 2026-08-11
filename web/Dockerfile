# 정적 빌드 후 nginx로 서빙. API/WS 오리진은 빌드 시점에 주입한다.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL
ARG VITE_WS_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_WS_URL=$VITE_WS_URL
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
# nginx 공식 이미지는 templates/*.template에 envsubst를 적용한다 — Railway의 PORT 대응.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
ENV PORT=80
EXPOSE 80
