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
 * @module ui
 *
 * UI rendering helpers – all direct DOM manipulation lives here.
 *
 * Functions are intentionally free of authentication and business logic so
 * that the rendering layer can be updated independently of the auth flow.
 */

/**
 * Displays a status message with the appropriate severity styling.
 *
 * @param {'info'|'success'|'error'} type - Visual severity class.
 * @param {string} message - Human-readable message to display.
 */
export function showStatus(type, message) {
  const el = document.getElementById('status');
  el.className = `status ${type}`;
  el.textContent = message;
}

/**
 * Enables or disables the login button to reflect a loading / in-progress state.
 *
 * @param {boolean} loading - `true` to disable the button, `false` to re-enable it.
 */
export function setLoading(loading) {
  document.getElementById('loginBtn').disabled = loading;
}

/**
 * Renders the Login Service tokens and switches the view to the post-login state
 * (hides the login button, shows the logout button and token section).
 *
 * @param {string} accessToken  - Short-lived Login Service access token.
 * @param {string} refreshToken - Long-lived Login Service refresh token.
 */
export function showTokens(accessToken, refreshToken) {
  document.getElementById('lsAccessToken').textContent  = accessToken;
  document.getElementById('lsRefreshToken').textContent = refreshToken;
  document.getElementById('tokenSection').classList.add('visible');
  document.getElementById('logoutBtn').style.display = 'block';
  document.getElementById('loginBtn').style.display  = 'none';
}

/**
 * Clears the token display and resets the view to the pre-login state
 * (shows the login button, hides the logout button and token section).
 */
export function hideTokens() {
  document.getElementById('lsAccessToken').textContent  = '';
  document.getElementById('lsRefreshToken').textContent = '';
  document.getElementById('tokenSection').classList.remove('visible');
  document.getElementById('logoutBtn').style.display = 'none';
  document.getElementById('loginBtn').style.display  = 'block';
}

/**
 * Copies the text content of a token display element to the clipboard and
 * shows a brief confirmation status message.
 *
 * @param {string} elementId - ID of the element whose text should be copied.
 */
export function copyToken(elementId) {
  const text = document.getElementById(elementId).textContent;
  navigator.clipboard.writeText(text).then(() => {
    showStatus('info', 'Token copied to clipboard.');
  });
}
