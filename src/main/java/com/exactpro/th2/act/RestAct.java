package com.exactpro.th2.act;

import com.exactpro.th2.act.grpc.GetLastMessageParams;
import com.exactpro.th2.act.grpc.RestWebActGrpc;
import com.exactpro.th2.act.grpc.RestWebActResponseDemo;
import com.exactpro.th2.act.grpc.RhBatchResponseDemo;
import io.grpc.stub.StreamObserver;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class RestAct extends RestWebActGrpc.RestWebActImplBase {
    private static final Logger logger = LoggerFactory.getLogger(HandWinAct.class);

    private final String apiHost;
    private final String apiBasePath;

    public RestAct(String apiHost, String apiBasePath) {
        this.apiHost = apiHost;
        this.apiBasePath = apiBasePath;
    }

    @Override
    public void getLastMessageFromProvider(GetLastMessageParams request, StreamObserver<RestWebActResponseDemo> responseObserver) {
        logger.debug("Executing getLastMessageFromProvider");
        final String stream = request.getStream();
        final String messageType = request.getMsgType();
        final String messageBody = request.getMsgBody();
        final String messageStreamString;

        try(CloseableHttpClient client = HttpClients.custom()
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
                .build()) {
            var builder = new URIBuilder();
            var requestURL = builder.setScheme("https")
                    .setHost(apiHost)
                    .setPath(apiBasePath + "search/sse/messages")
                    .addParameter("startTimestamp", String.valueOf(System.currentTimeMillis()))
                    .addParameter("stream", request.getStream())
                    .addParameter("searchDirection", "previous")
                    .addParameter("resultCountLimit", "200")
                    .addParameter("filters", "type")
                    .addParameter("filters", "body")
                    .addParameter("type-values", messageType)
                    .addParameter("body-values", messageBody)
                    .build();

            var httpGet = new HttpGet(requestURL);
            var response = client.execute(httpGet);
            messageStreamString = new String(response.getEntity().getContent().readAllBytes());
        } catch (GeneralSecurityException | URISyntaxException | IOException e) {
            logger.debug("GET request failed", e);
            responseObserver.onError(e);
            return;
        }

        var messageJson = extractMessageFromSse(messageStreamString);
        final RestWebActResponseDemo response = RestWebActResponseDemo.newBuilder()
                .setScriptStatusValue(0)
                .putData("message", messageJson)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
        logger.debug("getLastMessageFromProvider params: stream = " + stream + ", messageType = " + messageType + ", messageBody = " + messageBody);
        logger.debug("Execution finished (getLastMessageFromProvider)");
    }

    private String extractMessageFromSse(String stream) {
        return Arrays.stream(stream.split("\n"))
                .dropWhile(str -> !"event: message".equals(str))
                .skip(1)
                .takeWhile(str -> str.startsWith("data: "))
                .map(str -> str.substring(6))
                .collect(Collectors.joining());
    }
}