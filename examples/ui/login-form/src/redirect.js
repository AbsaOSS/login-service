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
 * @module redirect
 *
 * Entry point for redirect.html — the MSAL redirect bridge page.
 *
 * MSAL Browser v5 uses BroadcastChannel to communicate the authentication
 * response from the popup (or hidden iframe) back to the main application
 * window.  broadcastResponseToMainFrame() reads the auth response from the
 * current URL and broadcasts it over that channel, then the main window's
 * MSAL instance receives it and resolves the loginPopup() / acquireTokenPopup()
 * promise.
 *
 * This file intentionally contains no application logic.
 */

import { broadcastResponseToMainFrame } from '@azure/msal-browser/redirect-bridge';

broadcastResponseToMainFrame();
