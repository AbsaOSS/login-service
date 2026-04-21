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
 * @module auth
 *
 * Authentication module wrapping MSAL.js for Microsoft Entra ID (Azure AD).
 *
 * Responsibilities:
 *  - Create and configure a PublicClientApplication instance.
 *  - Handle the redirect promise on page load (redirect-flow fallback).
 *  - Perform popup login with automatic redirect fallback.
 *  - Acquire access tokens silently, falling back to popup when needed.
 *  - Logout via popup.
 *
 * This module contains no DOM manipulation and no Login Service calls;
 * those concerns live in ui.js and login-service-client.js respectively.
 */

import * as msal from '@azure/msal-browser';

/**
 * Creates and returns a configured MSAL {@link msal.PublicClientApplication}.
 *
 * @param {string} clientId - Azure AD application (client) ID.
 * @param {string} tenantId - Azure AD tenant (directory) ID.
 * @returns {msal.PublicClientApplication}
 */
export function createMsalInstance(clientId, tenantId) {
  return new msal.PublicClientApplication({
    auth: {
      clientId,
      authority: `https://login.microsoftonline.com/${tenantId}`,
      // Strip query params so Entra's redirect URI validation passes.
      redirectUri: window.location.href.split('?')[0],
    },
    cache: {
      cacheLocation: 'sessionStorage',
      storeAuthStateInCookie: false,
    },
  });
}

/**
 * Processes the redirect response when the browser returns from Entra's
 * login page (redirect-flow fallback).  Must be called once on page load.
 *
 * @param {msal.PublicClientApplication} msalInstance
 * @returns {Promise<msal.AuthenticationResult|null>} The auth result, or null
 *   if no redirect response is present.
 */
export function handleRedirectPromise(msalInstance) {
  return msalInstance.handleRedirectPromise();
}

/**
 * Logs the user in using a popup window.  If the popup is blocked by the
 * browser, falls back to a full-page redirect; in that case the function
 * returns `null` and the result will be picked up by
 * {@link handleRedirectPromise} after the page reloads.
 *
 * @param {msal.PublicClientApplication} msalInstance
 * @param {string[]} scopes - OAuth2 scopes to request (e.g. `["api://…/.default"]`).
 * @returns {Promise<msal.AuthenticationResult|null>}
 * @throws {Error} On any MSAL error other than a blocked popup.
 */
export async function login(msalInstance, scopes) {
  const loginRequest = { scopes };
  try {
    return await msalInstance.loginPopup(loginRequest);
  } catch (err) {
    if (err.errorCode === 'popup_window_error' || err.errorCode === 'empty_window_error') {
      // Popup blocked – initiate redirect; page reloads after Entra login.
      await msalInstance.loginRedirect(loginRequest);
      return null;
    }
    throw err;
  }
}

/**
 * Acquires an access token for the given account silently from the MSAL
 * cache / session.  Falls back to an interactive popup when the silent
 * request fails (e.g. consent required, session expired).
 *
 * @param {msal.PublicClientApplication} msalInstance
 * @param {msal.AccountInfo} account - The authenticated account.
 * @param {string[]} scopes - OAuth2 scopes for the token request.
 * @returns {Promise<msal.AuthenticationResult>}
 */
export async function acquireToken(msalInstance, account, scopes) {
  try {
    return await msalInstance.acquireTokenSilent({ scopes, account });
  } catch (_) {
    // Silent acquisition failed – fall back to interactive popup.
    return msalInstance.acquireTokenPopup({ scopes, account });
  }
}

/**
 * Logs the current user out via a popup and redirects back to this page.
 *
 * @param {msal.PublicClientApplication} msalInstance
 */
export function logout(msalInstance) {
  msalInstance.logoutPopup({ postLogoutRedirectUri: window.location.href });
}
