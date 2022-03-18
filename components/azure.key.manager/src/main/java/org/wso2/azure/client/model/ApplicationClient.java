package org.wso2.azure.client.model;

import org.wso2.carbon.apimgt.impl.kmclient.KeyManagerClientException;

import feign.Headers;
import feign.Param;
import feign.RequestLine;

public interface ApplicationClient {

	@RequestLine("POST /v1.0/applications")
	@Headers("Content-Type: application/json")
	public ApplicationInfo createApplication(ApplicationInfo applicationInfo)
			throws KeyManagerClientException;

	@RequestLine("GET /v1.0/applications/{id}")
	public ApplicationInfo getApplication(@Param("id") String id) throws KeyManagerClientException;

	@RequestLine("DELETE /v1.0/applications/{id}")
	public void deleteApplication(@Param("id") String id) throws KeyManagerClientException;

	// TODO: resource to update application
	// TODO: resource to retrieve all applications from azure
}
