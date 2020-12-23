FROM node:10.16.3-alpine as build
WORKDIR /src/
RUN apk add --no-cache --virtual .gyp \
        python make g++ zlib-dev \
        git python build-base \ 
        autoconf automake nasm

COPY development/package*.json /src/
RUN npm ci

ARG APP_ENV
ARG BB_PLATFORM
ARG ENVBUILD
ENV BB_PLATFORM=${BB_PLATFORM:-web} \
    ENVBUILD=${ENVBUILD:-public-dev} \
    NODE_ENV=${APP_ENV:-development} \
    NODE_OPTIONS="--max-old-space-size=4096"

COPY development/ .
RUN npm run lint -- web-app &&\
    npm run build -- --project=web-app --configuration=${ENVBUILD}

### Copy static to nginx container
FROM nginx:1.17.4-alpine as static
EXPOSE 80
CMD ["/usr/sbin/nginx"]
WORKDIR /app/
ADD ci/docker/rootfs /
COPY --from=build /src/dist/web-app/ /app/
