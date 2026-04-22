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
 * @module login-service-client
 *
 * HTTP client for the Login Service REST API.
 *
 * Responsibility: exchange a Microsoft Entra access token for Login Service
 * JWT tokens by calling `POST /token/generate`.
 *
 * This module contains no DOM manipulation and no MSAL logic.
 */

/**
 * @typedef {Object} LoginServiceTokens
 * @property {string} token   - Short-lived Login Service access token (JWT).
 * @property {string} refresh - Long-lived Login Service refresh token (JWT).
 */

/**
 * Exchanges a Microsoft Entra access token for Login Service JWT tokens.
 *
 * The Login Service must be running and must have CORS configured to allow
 * requests from the origin serving this page.
 *
 * @param {string} loginServiceHost - Base URL of the Login Service (e.g. `http://localhost:9090`).
 * @param {string} entraToken       - Entra access token obtained from MSAL.
 * @returns {Promise<LoginServiceTokens>}
 * @throws {Error} On network failure or a non-2xx HTTP response.
 */
export async function exchangeToken(loginServiceHost, entraToken) {
  const url = `${loginServiceHost}/token/generate`;

  let response;
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${entraToken}`,
        Accept: 'application/json',
      },
    });
  } catch (networkErr) {
    throw new Error(
      `Network error contacting Login Service (${url}). ` +
      `Ensure the service is running and CORS is enabled for this origin. ` +
      `Details: ${networkErr.message}`,
    );
  }

  if (!response.ok) {
    const body = await response.text().catch(() => '');
    throw new Error(`Login Service returned HTTP ${response.status}. ${body}`);
  }

  return response.json();
}
