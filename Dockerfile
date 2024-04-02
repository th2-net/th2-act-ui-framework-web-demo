FROM adoptopenjdk/openjdk11:alpine as libwebp
ARG libwebp_version=v1.2.0
WORKDIR /home
RUN apk add --no-cache make git gcc musl-dev swig \
    && git clone --branch ${libwebp_version} --single-branch https://chromium.googlesource.com/webm/libwebp  \
    && cd libwebp \
    && sed -i '17,20d' makefile.unix \
    && make -f makefile.unix CPPFLAGS="-I. -Isrc/ -Wall -fPIC" \
    && cd swig \
    && mkdir -p java/com/exactpro/remotehand/screenwriter \
    && swig -java -package com.exactpro.remotehand.screenwriter \
        -outdir java/com/exactpro/remotehand/screenwriter -o libwebp_java_wrap.c libwebp.swig \
    && gcc -shared -fPIC -fno-strict-aliasing -O2 -I/opt/java/openjdk/include/ -I/opt/java/openjdk/include/linux \
        -I../src -L../src libwebp_java_wrap.c -lwebp -o libwebp.so

FROM gradle:7.6-jdk11 AS build
ARG release_version
ARG vcs_url
COPY ./ .
RUN gradle --debug --stacktrace --no-daemon clean build dockerPrepare -Prelease_version=${release_version} -Pvcs_url=${vcs_url}

FROM adoptopenjdk/openjdk11:alpine
ENV GRPC_PORT=8080 \
    RABBITMQ_HOST=rabbitmq \
    RABBITMQ_PORT=5672 \
    RABBITMQ_USER="" \
    RABBITMQ_PASS="" \
    RABBITMQ_VHOST=th2 \
    RABBITMQ_EXCHANGE_NAME_TH2_CONNECTIVITY="" \
    TH2_VERIFIER_GRPC_HOST="" \
    TH2_VERIFIER_GRPC_PORT="" \
    TH2_EVENT_STORAGE_GRPC_HOST="" \
    TH2_EVENT_STORAGE_GRPC_PORT="" \
    #FIXME: Act should resolve queue information from session info which passed by caller (script)
    TH2_FIX_CONNECTIVITY_SEND_MQ="" \
    #FIXME: Act should resolve queue information from session info which passed by caller (script)
    TH2_FIX_CONNECTIVITY_IN_MQ=""
WORKDIR /home
COPY --from=libwebp /home/libwebp/swig/libwebp.so ./
COPY --from=build /home/gradle/build/docker .
ENTRYPOINT ["/home/service/bin/service", "/home/service/etc/config.yml"]