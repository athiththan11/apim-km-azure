package org.wso2.azure.client.model;

import com.google.gson.annotations.SerializedName;

public class AccessToken {
    
    @SerializedName("access_token")
    private String token;
    @SerializedName("token_type")
    private String tokenType;
    @SerializedName("expires_in")
    private long expiry;

    public String getAccessToken() {
        return token;
    }
    public void setAccessToken(String accessToken) {
        this.token = accessToken;
    }
    public String getTokenType() {
        return tokenType;
    }
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
    public long getExpiry() {
        return expiry;
    }
    public void setExpiry(long expiry) {
        this.expiry = expiry;
    }
}
