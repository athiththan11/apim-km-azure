# Integrate Azure AD with WSO2 API Manager

This explains how to integrate Azure AD as a third-party Key Manager with WSO2 API Manager (v3.2.0 onwards).

## Build & Deploy

To build the project, execute the following `mvn` command from the root directory of the project

```sh
mvn clean install package
```

After successful build, copy the built JAR artifact from `<apim-km-azure>/components/azure.key.manager/target` directory and place it inside the `<apim>/repository/components/dropins` directory of the WSO2 API Manager server.

This step requires a server restart to take effect on the changes and to enable the Azure AD Key Manager component in API Manager.

## Configure Azure AD

Given instructions are pre-requisite steps to configure Azure AD to integrate with WSO2 API Manager.

> Since, Microsoft or Azure doesn't provide any OpenID Configuration endpoints to dynamically register clients, this implementation focuses on using the Microsoft's GraphAPI to perform the same to an extent

### Create Azure AD Application

<!-- TODO: -->
- Navigate to `App registrations`
- Click on `New registration`
- Name: `Azure Graph App`
- Supported account types: `Accounts in this organizational directory only`
- Click `Register`

- Go to `Certificates & secrets` > `Client secrets`
- `New client secret`
- Description: `WSO2 API Manager`
- Expires: `Custom` (and provide a longer a value)

## Configure WSO2 API Manager
