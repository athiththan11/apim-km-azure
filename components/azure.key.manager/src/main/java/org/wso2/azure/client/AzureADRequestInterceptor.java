package org.wso2.azure.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.Gson;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.wso2.azure.client.model.AccessToken;
import org.wso2.carbon.apimgt.impl.APIConstants;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class AzureADRequestInterceptor implements RequestInterceptor {
    private String tokenEndpoint;
    private String clientId;
    private String clientSecret;
    private String accessToken;

    private static final Log LOG = LogFactory.getLog(AzureADRequestInterceptor.class);

    public AzureADRequestInterceptor(String tokenEndpoint, String clientId, String clientSecret) {
        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        getAccessToken();
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {
        requestTemplate
                .header(APIConstants.AUTHORIZATION_HEADER_DEFAULT, APIConstants.AUTHORIZATION_BEARER + accessToken);
    }

    private void getAccessToken() {
        List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair(AzureADConstants.GRANT_TYPE,
                (String) AzureADConstants.CLIENT_CREDENTIALS_GRANT_TYPE));
        parameters.add(new BasicNameValuePair(AzureADConstants.SCOPE,
                "https://graph.microsoft.com/.default"));

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {

            HttpPost httpPost = new HttpPost(tokenEndpoint);
            httpPost.setEntity(new UrlEncodedFormEntity(parameters));

            String encodedCreds = Base64.getEncoder()
                    .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            httpPost.setHeader(HttpHeaders.AUTHORIZATION, AzureADConstants.BASIC + encodedCreds);
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, AzureADConstants.CONTENT_TYPE_URL_ENCODED);

            HttpResponse resp = httpClient.execute(httpPost);
            int statusCode = resp.getStatusLine().getStatusCode();

            HttpEntity entity = resp.getEntity();
            if (entity == null) {
                LOG.error("Could not read HTTP entity for response " + resp, null);
            }

            if (statusCode == HttpStatus.SC_OK) {
                try (InputStream inputStream = entity.getContent()) {
                    String content = IOUtils.toString(inputStream);
                    accessToken = new Gson().fromJson(content, AccessToken.class).getAccessToken();
                }
            }
        } catch (IOException e) {
            LOG.error("Error occured while reading or closing the buffer reader", e);
        }
    }
}
