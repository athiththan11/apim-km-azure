package org.wso2.azure.client.model;

import org.wso2.carbon.apimgt.impl.kmclient.KeyManagerClientException;

import feign.Headers;
import feign.Param;
import feign.RequestLine;

public interface PasswordClient {
    
    @RequestLine("POST /v1.0/applications/{id}/addPassword")
    @Headers("Content-Type: application/json")
    public PasswordInfo addPassword(@Param("id") String id, PasswordInfo pInfo) throws KeyManagerClientException;

}
