/*
 * Copyright 2023 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Login-Form UI Example Configuration
 *
 * Fill in these values to match your deployment.
 * The values here mirror the YAML keys in example.application.yaml:
 *
 *   login-service.example.host           → loginServiceHost
 *   login-service.example.entra.tenantId → entra.tenantId
 *   login-service.example.entra.clientId → entra.clientId
 *   login-service.example.entra.scopes   → entra.scopes
 */
const LOGIN_FORM_CONFIG = {

  // Base URL of the running login-service instance.
  // Matches login-service.example.host in example.application.yaml.
  loginServiceHost: "http://localhost:9090",

  entra: {
    // Azure AD tenant ID (directory ID).
    // Matches login-service.example.entra.tenant-id in example.application.yaml.
    tenantId: "your-tenant-id",

    // Application (client) ID of the Entra app registration used by this UI.
    // Matches login-service.example.entra.client-id in example.application.yaml.
    clientId: "your-client-id",

    // OAuth2 scopes to request when acquiring the Entra access token.
    // The token's audience must match one of the values accepted by the
    // login-service entra.audiences config (or be left empty to accept any).
    // Matches login-service.example.entra.scopes in example.application.yaml.
    scopes: ["api://your-client-id/.default"],
  },
};
