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
 * @module app
 *
 * Application entry point.
 *
 * Reads runtime configuration from the globally available `LOGIN_FORM_CONFIG`
 * (set by config.js before this bundle is loaded), then wires together the
 * three specialised modules:
 *
 *  - {@link module:auth}                 – Microsoft Entra / MSAL authentication
 *  - {@link module:login-service-client} – Login Service token exchange
 *  - {@link module:ui}                   – DOM rendering
 *
 * No DOM manipulation or HTTP calls are made directly from this module.
 */

/* global LOGIN_FORM_CONFIG */
import * as auth         from './auth.js';
import { exchangeToken } from './login-service-client.js';
import * as ui           from './ui.js';

const { loginServiceHost, entra } = LOGIN_FORM_CONFIG;

// ---------------------------------------------------------------------------
// MSAL initialisation
// ---------------------------------------------------------------------------

const msalInstance = auth.createMsalInstance(entra.clientId, entra.tenantId);

// ---------------------------------------------------------------------------
// Orchestration
// ---------------------------------------------------------------------------

/**
 * Called after a successful Entra authentication (popup or redirect).
 *
 * Acquires an Entra access token and exchanges it with the Login Service,
 * then updates the UI with the resulting tokens.
 *
 * @param {import('@azure/msal-browser').AuthenticationResult} authResult
 */
async function onAuthenticated(authResult) {
  const { account } = authResult;
  msalInstance.setActiveAccount(account);

  ui.showStatus('info', `Authenticated as ${account.username}. Acquiring token…`);

  let tokenResponse;
  try {
    tokenResponse = await auth.acquireToken(msalInstance, account, entra.scopes);
  } catch (err) {
    ui.showStatus('error', `Token acquisition failed: ${err.message}`);
    ui.setLoading(false);
    return;
  }

  ui.showStatus('info', `Exchanging Entra token with Login Service at ${loginServiceHost}…`);

  let tokens;
  try {
    tokens = await exchangeToken(loginServiceHost, tokenResponse.accessToken);
  } catch (err) {
    ui.showStatus('error', err.message);
    ui.setLoading(false);
    return;
  }

  ui.showTokens(tokens.token, tokens.refresh);
  ui.showStatus('success', 'Login successful! Your Login Service tokens are displayed below.');
  ui.setLoading(false);
}

// ---------------------------------------------------------------------------
// Initialisation & event listeners
// ---------------------------------------------------------------------------

(async () => {
  await msalInstance.initialize();

  // Process any pending redirect response immediately on page load.
  auth.handleRedirectPromise(msalInstance)
    .then(result => { if (result) onAuthenticated(result); })
    .catch(err   => ui.showStatus('error', `MSAL redirect error: ${err.message}`));

  document.getElementById('loginBtn').addEventListener('click', async () => {
    ui.setLoading(true);
    ui.showStatus('info', 'Opening Microsoft login…');

    try {
      const result = await auth.login(msalInstance, entra.scopes);
      // result is null when a redirect was initiated; onAuthenticated will be
      // called after the page reloads via handleRedirectPromise above.
      if (result) await onAuthenticated(result);
    } catch (err) {
      ui.showStatus('error', `Authentication failed: ${err.message}`);
      ui.setLoading(false);
    }
  });

  document.getElementById('logoutBtn').addEventListener('click', () => {
    auth.logout(msalInstance);
    ui.hideTokens();
    ui.showStatus('info', 'Signed out.');
  });

  document.querySelectorAll('.copy-btn[data-copy]').forEach(btn => {
    btn.addEventListener('click', () => ui.copyToken(btn.dataset.copy));
  });
})();
