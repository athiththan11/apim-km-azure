package org.wso2.azure.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
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
import org.wso2.azure.client.model.ApplicationClient;
import org.wso2.azure.client.model.ApplicationInfo;
import org.wso2.azure.client.model.PasswordClient;
import org.wso2.azure.client.model.PasswordCredential;
import org.wso2.azure.client.model.PasswordInfo;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIKey;
import org.wso2.carbon.apimgt.api.model.AccessTokenInfo;
import org.wso2.carbon.apimgt.api.model.AccessTokenRequest;
import org.wso2.carbon.apimgt.api.model.ApplicationConstants;
import org.wso2.carbon.apimgt.api.model.KeyManagerConfiguration;
import org.wso2.carbon.apimgt.api.model.OAuthAppRequest;
import org.wso2.carbon.apimgt.api.model.OAuthApplicationInfo;
import org.wso2.carbon.apimgt.api.model.Scope;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.AbstractKeyManager;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.kmclient.ApacheFeignHttpClient;
import org.wso2.carbon.apimgt.impl.kmclient.KMClientErrorDecoder;
import org.wso2.carbon.apimgt.impl.kmclient.KeyManagerClientException;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import edu.emory.mathcs.backport.java.util.Collections;
import feign.Feign;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;
import feign.slf4j.Slf4jLogger;

public class AzureADClient extends AbstractKeyManager {
    private ApplicationClient appClient;
    private PasswordClient passwordClient;
    private String azureADClientId;
    private String azureADClientSecret;
    private String azureADTenantId;

    private static Map<String, Map<String, String>> appMap = new HashMap<>();
    private static final Log LOG = LogFactory.getLog(AzureADClient.class);

    @Override
    public OAuthApplicationInfo createApplication(OAuthAppRequest oauthAppRequest) throws APIManagementException {
        OAuthApplicationInfo oauthAppInfo = oauthAppRequest.getOAuthApplicationInfo();

        if (oauthAppInfo != null) {
            ApplicationInfo appInfo = createApplicationInfo(oauthAppInfo);
            ApplicationInfo app;

            try {
                app = appClient.createApplication(appInfo);
                if (app != null) {
                    PasswordInfo req = new PasswordInfo();
                    PasswordCredential cred = new PasswordCredential();
                    cred.setDisplayName("app_secret");
                    req.setPwdCredential(cred);

                    PasswordInfo pInfo = passwordClient.addPassword(app.getId(), req);
                    app.setClientSecret(pInfo.getSecret());

                    Map<String, String> map = new HashMap<>();
                    map.put("secret", pInfo.getSecret());
                    map.put("id", app.getId());
                    appMap.put(app.getClientId(), map);

                    return createOAuthApplicationInfo(app);
                }
            } catch (KeyManagerClientException e) {
                handleException("Error occured while creating Azure AD Application", e);
            }
        }

        return null;
    }

    private OAuthApplicationInfo createOAuthApplicationInfo(ApplicationInfo appInfo) {
        OAuthApplicationInfo oauthAppInfo = new OAuthApplicationInfo();
        oauthAppInfo.setClientName(appInfo.getAppName());
        oauthAppInfo.setClientId(appInfo.getClientId());
        oauthAppInfo.setClientSecret(appInfo.getClientSecret());

        if (StringUtils.isNotEmpty(appInfo.getAppName())) {
            oauthAppInfo.addParameter(ApplicationConstants.OAUTH_CLIENT_NAME, appInfo.getAppName());
        }

        if (StringUtils.isNotEmpty(appInfo.getClientId())) {
            oauthAppInfo.addParameter(ApplicationConstants.OAUTH_CLIENT_ID, appInfo.getClientId());
        }

        if (StringUtils.isNotEmpty(appInfo.getClientSecret())) {
            oauthAppInfo.addParameter(ApplicationConstants.OAUTH_CLIENT_SECRET, appInfo.getClientSecret());
        }

        oauthAppInfo.addParameter(ApplicationConstants.OAUTH_CLIENT_GRANT,
                AzureADConstants.CLIENT_CREDENTIALS_GRANT_TYPE);

        String additonalProperties = new Gson().toJson(appInfo);
        oauthAppInfo.addParameter(APIConstants.JSON_ADDITIONAL_PROPERTIES,
                new Gson().fromJson(additonalProperties, Map.class));

        return oauthAppInfo;
    }

    private ApplicationInfo createApplicationInfo(OAuthApplicationInfo oauthAppInfo) {
        ApplicationInfo appInfo = new ApplicationInfo();

        String applicationName = oauthAppInfo.getClientName();

        String userId = (String) oauthAppInfo.getParameter(ApplicationConstants.OAUTH_CLIENT_USERNAME);
        String userNameForSp = MultitenantUtils.getTenantAwareUsername(userId);
        String keyType = (String) oauthAppInfo.getParameter(ApplicationConstants.APP_KEY_TYPE);
        if (keyType != null) {
            applicationName = userNameForSp.concat("_").concat(applicationName).concat("_").concat(keyType);
        }

        appInfo.setAppName(applicationName);
        return appInfo;
    }

    @Override
    public OAuthApplicationInfo updateApplication(OAuthAppRequest oauthAppRequest) throws APIManagementException {
        OAuthApplicationInfo oauthAppInfo = oauthAppRequest.getOAuthApplicationInfo();
        return oauthAppInfo;
    }

    @Override
    public void deleteApplication(String consumerKey) throws APIManagementException {
        Map<String, String> app = appMap.get(consumerKey);
        if (app == null) {
            getAppMetadata(consumerKey);
        }

        if (app != null && app.get("id") != null) {
            try {
                appClient.deleteApplication(app.get("id"));
                appMap.remove(consumerKey);
            } catch (KeyManagerClientException e) {
                handleException("Error occured while deleting Azure AD Application", e);
            }
        }
    }

    private void getAppMetadata(String clientId) throws APIManagementException {
        if (StringUtils.isNotEmpty(clientId)) {

            ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
            int applicationId = apiMgtDAO.getApplicationByClientId(clientId).getId();
            Set<APIKey> apiKeys = apiMgtDAO.getKeyMappingsFromApplicationId(applicationId);
            for (APIKey apiKey : apiKeys) {
                if (clientId.equals(apiKey.getConsumerKey()) && StringUtils.isNotEmpty(apiKey.getAppMetaData())) {
                    OAuthApplicationInfo storedOAuthApplicationInfo = new Gson().fromJson(apiKey.getAppMetaData(),
                            OAuthApplicationInfo.class);
                    String id = (String) ((Map<String, Object>) storedOAuthApplicationInfo
                            .getParameter(APIConstants.JSON_ADDITIONAL_PROPERTIES))
                            .get("id");
                    String clientSecret = apiKey.getConsumerSecret();

                    Map<String, String> meta = new HashMap<>();
                    meta.put("secret", clientSecret);
                    meta.put("id", id);
                    appMap.put(clientId, meta);
                }
            }
        }
    }

    private String generateAzureAccessToken() throws APIManagementException {
        try {
            List<NameValuePair> parameters = new ArrayList<>();
            parameters.add(new BasicNameValuePair(AzureADConstants.GRANT_TYPE,
                    (String) AzureADConstants.CLIENT_CREDENTIALS_GRANT_TYPE));
            parameters.add(new BasicNameValuePair(AzureADConstants.SCOPE,
                    "https://graph.microsoft.com/.default"));
            AccessToken tokenResp;
            tokenResp = generateAccessToken(azureADClientId, azureADClientSecret, parameters);
            if (tokenResp != null) {
                return tokenResp.getAccessToken();
            }
        } catch (APIManagementException e) {
            handleException("Error occured while trying to generate access token for internal communication", e);
        }

        return null;
    }

    @Override
    public OAuthApplicationInfo retrieveApplication(String consumerKey) throws APIManagementException {
        Map<String, String> app = appMap.get(consumerKey);
        if (app == null) {
            getAppMetadata(consumerKey);
        }

        if (app != null && app.get("id") != null && app.get("secret") != null) {
            ApplicationInfo appInfo;
            try {
                appInfo = appClient.getApplication(app.get("id"));
                if (appInfo != null) {
                    appInfo.setClientSecret(app.get("secret"));
                    return createOAuthApplicationInfo(appInfo);
                }
            } catch (KeyManagerClientException e) {
                handleException("Error occured while retrieving Azure AD Application", e);
            }
        }

        return null;
    }

    @Override
    public AccessTokenInfo getNewApplicationAccessToken(AccessTokenRequest tokenRequest) throws APIManagementException {
        String clientId = tokenRequest.getClientId();
        String clientSecret = tokenRequest.getClientSecret();

        Object grantType = tokenRequest.getGrantType();
        if (grantType == null) {
            grantType = AzureADConstants.CLIENT_CREDENTIALS_GRANT_TYPE;
        }

        List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair(AzureADConstants.GRANT_TYPE, (String) grantType));

        // ? setting microsoft's default graph api scope. this implementation needs to
        // ? be changed to support other scopes
        parameters.add(new BasicNameValuePair(AzureADConstants.SCOPE, AzureADConstants.MICROSOFT_DEFAULT_SCOPE));

        AccessToken tokenResp = generateAccessToken(clientId, clientSecret, parameters);
        AccessTokenInfo tokenInfo = new AccessTokenInfo();
        if (tokenResp != null) {
            tokenInfo.setConsumerKey(clientId);
            tokenInfo.setConsumerSecret(clientSecret);
            tokenInfo.setAccessToken(tokenResp.getAccessToken());
            tokenInfo.setScope(new String[] { AzureADConstants.MICROSOFT_DEFAULT_SCOPE });
            tokenInfo.setValidityPeriod(tokenResp.getExpiry());
            return tokenInfo;
        }

        tokenInfo.setTokenValid(false);
        tokenInfo.setErrorcode(APIConstants.KeyValidationStatus.API_AUTH_INVALID_CREDENTIALS);

        return tokenInfo;
    }

    private AccessToken generateAccessToken(String clientId, String clientSecret, List<NameValuePair> parameters)
            throws APIManagementException {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            String tokenEndpoint = (String) this.configuration.getParameter(APIConstants.KeyManager.TOKEN_ENDPOINT);

            HttpPost httpPost = new HttpPost(tokenEndpoint);
            httpPost.setEntity(new UrlEncodedFormEntity(parameters));

            String encodedCreds = getEncodedCredentials(clientId, clientSecret);
            httpPost.setHeader(HttpHeaders.AUTHORIZATION, AzureADConstants.BASIC + encodedCreds);
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, AzureADConstants.CONTENT_TYPE_URL_ENCODED);

            HttpResponse resp = httpClient.execute(httpPost);
            int statusCode = resp.getStatusLine().getStatusCode();

            HttpEntity entity = resp.getEntity();
            if (entity == null) {
                handleException("Could not read HTTP entity for response " + resp, null);
            }

            if (statusCode == HttpStatus.SC_OK) {
                try (InputStream inputStream = entity.getContent()) {
                    String content = IOUtils.toString(inputStream);
                    return new Gson().fromJson(content, AccessToken.class);
                }
            }
        } catch (IOException e) {
            handleException("Error occured while reading or closing the buffer reader", e);
        }

        return null;
    }

    @Override
    public KeyManagerConfiguration getKeyManagerConfiguration() throws APIManagementException {
        return this.configuration;
    }

    @Override
    public OAuthApplicationInfo mapOAuthApplication(OAuthAppRequest appInfoRequest) throws APIManagementException {
        // not applicable
        return null;
    }

    @Override
    public void loadConfiguration(KeyManagerConfiguration configuration) throws APIManagementException {
        this.configuration = configuration;

        // the following configs are required to communicate with the azure ad
        // 1. Azure AD App Consumer ID
        // 2. Azure AD App Consumer Secret
        // 3. Azure AD App Tenant ID

        // request for the microsoft graph api endpoint via the admin portal
        // configurations

        azureADClientId = (String) this.configuration.getParameter(AzureADConstants.AD_APP_CLIENT_ID);
        azureADClientSecret = (String) this.configuration.getParameter(AzureADConstants.AD_APP_CLIENT_SECRET);
        azureADTenantId = (String) this.configuration.getParameter(AzureADConstants.AD_APP_TENANT);

        String applicationEndpoint = (String) this.configuration
                .getParameter(AzureADConstants.GRAPH_API_ENDPOINT);
        String passwordEndpoint = (String) this.configuration.getParameter(AzureADConstants.GRAPH_API_ENDPOINT);

        appClient = Feign.builder().client(new ApacheFeignHttpClient(APIUtil.getHttpClient(applicationEndpoint)))
                .encoder(new GsonEncoder()).decoder(new GsonDecoder()).errorDecoder(
                        new KMClientErrorDecoder())
                .requestInterceptor(new AzureADRequestInterceptor(
                        (String) this.configuration.getParameter(
                                APIConstants.KeyManager.TOKEN_ENDPOINT),
                        azureADClientId, azureADClientSecret))
                .logger(new Slf4jLogger()).target(ApplicationClient.class, applicationEndpoint);

        passwordClient = Feign.builder().client(new ApacheFeignHttpClient(APIUtil.getHttpClient(passwordEndpoint)))
                .encoder(new GsonEncoder()).decoder(new GsonDecoder()).errorDecoder(new KMClientErrorDecoder())
                .requestInterceptor(new AzureADRequestInterceptor(
                        (String) this.configuration.getParameter(
                                APIConstants.KeyManager.TOKEN_ENDPOINT),
                        azureADClientId, azureADClientSecret))
                .logger(new Slf4jLogger()).target(PasswordClient.class, passwordEndpoint);
    }

    private String getEncodedCredentials(String clientId, String clientSecret) {
        return Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getNewApplicationConsumerSecret(AccessTokenRequest tokenRequest) throws APIManagementException {
        return null;
    }

    @Override
    public AccessTokenInfo getTokenMetaData(String accessToken) throws APIManagementException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean registerNewResource(API api, Map map) throws APIManagementException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Map getResourceByApiId(String apiId) throws APIManagementException {
        return null;
    }

    @Override
    public boolean updateRegisteredResource(API api, Map resourceAttributes) throws APIManagementException {
        return false;
    }

    @Override
    public void deleteRegisteredResourceByAPIId(String apiID) throws APIManagementException {
        // not applicable
    }

    @Override
    public void deleteMappedApplication(String consumerKey) throws APIManagementException {
        // not applicable
    }

    @Override
    public Set<String> getActiveTokensByConsumerKey(String consumerKey) throws APIManagementException {
        return Collections.emptySet();
    }

    @Override
    public AccessTokenInfo getAccessTokenByConsumerKey(String consumerKey) throws APIManagementException {
        return null;
    }

    @Override
    public Map<String, Set<Scope>> getScopesForAPIS(String apiIdsString) throws APIManagementException {
        return null;
    }

    @Override
    public void registerScope(Scope scope) throws APIManagementException {
        // not applicable
    }

    @Override
    public Scope getScopeByName(String name) throws APIManagementException {
        return null;
    }

    @Override
    public Map<String, Scope> getAllScopes() throws APIManagementException {
        return null;
    }

    @Override
    public void deleteScope(String scopeName) throws APIManagementException {
        // not applicable
    }

    @Override
    public void updateScope(Scope scope) throws APIManagementException {
        // not applicable
    }

    @Override
    public boolean isScopeExists(String scopeName) throws APIManagementException {
        return false;
    }

    @Override
    public String getType() {
        return AzureADConstants.AZURE_AD;
    }
}
