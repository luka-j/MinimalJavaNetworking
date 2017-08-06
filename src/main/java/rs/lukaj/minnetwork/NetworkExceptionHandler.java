/*
  Minimal Java Networking - a barebones networking library for Java and Android
  Copyright (C) 2017 Luka Jovičić

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published
  by the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package rs.lukaj.minnetwork;

import java.io.IOException;


/**
 * Handles all possible network errors. Implementers shouldn't assume code is run on UI
 * thread (as a matter of fact, it will always be executed in the background)
 * Created by luka on 3.1.16..
 */
public interface NetworkExceptionHandler {

    //if error mentions invalid http version, check whether spaces are properly encoded

    /**
     * User isn't logged in. Token should be cleared and user should be redirected to the login screen.
     */
    void handleUserNotLoggedIn();

    /**
     * User doesn't have appropriate permissions to access something on server. When UI is properly
     * implemented, this can only happen because (more likely) dev screwed up, or (less likely) user
     * has been tampering with this app's data using a tool such as root explorer.
     */
    void handleInsufficientPermissions(String message);

    /**
     * Unspecified server error has occured. Display a polite error message.
     */
    void handleServerError(String message);

    /**
     * Requested resource isn't found. Most of the times it's the dev's fault (user shouldn't
     * be able to make impossible requests).
     * @param code 404 or 410. If it's 410 you screwed something up badly.
     */
    void handleNotFound(int code);

    /**
     * User is trying to create duplicate something that shouldn't have duplicates.
     * Sometimes this is possible (i.e. upon registration, when registering with email
     * already in use, in which case user should be prompted to pick some other), other
     * times it's dev's error or result of illegal tampering (every other case AFAIR).
     */
    void handleDuplicate();

    /**
     * Invalid request. Bad types, bad ranges, missing values, superfluous values,... god knows.
     * Consult message for details.
     * @param message possible details for the error
     */
    void handleBadRequest(String message);

    /**
     * JSON parsing exceptions. This really should never happen, and either means malformed
     * json coming from server, or broken parsing code on client. Both should be caught early.
     */
    void handleJsonException();

    /**
     * Server is currently offline due to maintenance, and will be back soon (well, let's hope so).
     * @param until optional, denotes time when maintenance will end
     */
    void handleMaintenance(String until);

    /**
     * Server is unreachable. Check your internet connection (and whether you're connecting to
     * localhost on mobile data). If you're connected and it's still unreachable someone blew
     * up our servers.
     */
    void handleUnreachable();

    /**
     * IO Exception occurred. Could be due to network, or something to do with files. Dunno.
     * @param ex details of the exception
     */
    void handleIOException(IOException ex);

    /**
     * User has an invalid token, or is trying to access something that is off-the-limits.
     * Not much to do about it, show a message and hope it's nothing serious.
     * @param errorMessage "Invalid" if token is invalid, god-knows-what if everything else
     */
    void handleUnauthorized(String errorMessage);

    /**
     * User is making excessive requests from this device, and server is telling to cool
     * down.
     * @param retryAfter optional, time at which additional requests can be made
     */
    void handleRateLimited(String retryAfter);

    /**
     * Bad gateway. One of those errors I never truly understood, but basically something
     * happened on the server. Display a message to try again, maybe it'll fix itself.
     */
    void handleBadGateway();

    /**
     * See {@link #handleBadGateway()}, but with more hope it will fix itself.
     * Can also occur if server is under massive load.
     */
    void handleGatewayTimeout();

    /**
     * This usually means someone is trying to be clever and send a too large request.
     * Ask him to try something shorter/smaller, because server has its limits.
     */
    void handleEntityTooLarge();

    /**
     * Something has gone really wrong and this time we have no idea what. This shouldn't
     * be called for normal codes (i.e. <=400), so good luck figuring out what it is.
     * @param responseCode response code for which there is no appropriate handle
     * @param message possible details on the error
     */
    void handleUnknownHttpCode(int responseCode, String message);

    /**
     * Optional, can be called upon request completion, depending on the implementation.
     */
    void finished();


    /**
     * Reference implementation, methods can be overrided as necessary.
     * Uses InfoDialog to display error messages on the hostActivity
     * Provides finishedSuccessfully and finishedUnsucessfully methods, as well
     * as pinning down the IOException cause (file/socket/unknown)
     *
     * Can be used as a reference, but will probably need to be rewritten
     */
    /*class DefaultHandler implements NetworkExceptionHandler {
        private static final   String  LOGGING_TAG = "net.defaulthandler";
        protected static final String  TAG_DIALOG  = "studygroup.dialog.error";
        protected              boolean hasErrors   = false;
        protected AppCompatActivity hostActivity;

        public DefaultHandler(AppCompatActivity host) {
            hostActivity = host;
        }

        private void showErrorDialog(final String title, final String message) {
            hostActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    InfoDialog dialog = InfoDialog.newInstance(title, message);
                    if(hostActivity instanceof InfoDialog.Callbacks)
                        dialog.registerCallbacks((InfoDialog.Callbacks)hostActivity);
                    dialog.show(hostActivity.getSupportFragmentManager(), TAG_DIALOG);
                }
            });
        }
        private void showErrorDialog(final @StringRes int title, final @StringRes int message) {
            showErrorDialog(hostActivity.getString(title), hostActivity.getString(message));
        }

        @Override
        public void handleUserNotLoggedIn() {
            User.clearToken();
            showErrorDialog(R.string.error_session_expired_title, R.string.error_session_expired_text);
            hostActivity.startActivity(new Intent(hostActivity, LoginActivity.class));
            hasErrors=true;
        }

        @Override
        public void handleInsufficientPermissions(String message) {
            showErrorDialog(R.string.error_insufficient_permissions_title, R.string.error_insufficient_permissions_text);
            hasErrors=true;
        }

        @Override
        public void handleServerError(String message) {
            showErrorDialog(R.string.error_server_error_title, R.string.error_server_error_text);
            hasErrors=true;
        }

        @Override
        public void handleNotFound(final int code) {
            showErrorDialog(String.valueOf(code) + " " + hostActivity.getString(R.string.error_not_found_title),
                            hostActivity.getString(R.string.error_not_found_text));
            hasErrors=true;
        }

        @Override
        public void handleDuplicate() {
            showErrorDialog(R.string.error_duplicate_title, R.string.error_duplicate_text);
            hasErrors=true;
        }

        @Override
        public void handleBadRequest(String message) {
            showErrorDialog(R.string.error_bad_request_title, R.string.error_bad_request_text);
            hasErrors=true;
        }

        @Override
        public void handleJsonException() {
            showErrorDialog(R.string.error_json_title, R.string.error_json_text);
            hasErrors=true;
            finishedUnsuccessfully();
        }

        @Override
        public void handleMaintenance(String until) {
            showErrorDialog(R.string.error_maintenance_title, R.string.error_maintenance_text);
            hasErrors=true;
        }

        @Override
        public void handleUnreachable() {
            showErrorDialog(R.string.error_unreachable_title, R.string.error_unreachable_text);
            hasErrors=true;
        }

        @Override
        public void finished() {
            if(!hasErrors)
                hostActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finishedSuccessfully();
                    }
                });
            else
                hostActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finishedUnsuccessfully();
                    }
                });
            hasErrors=false;
        }

        @Override
        public void handleUnauthorized(String errorMessage) {
            showErrorDialog(R.string.error_unauthorized_title, R.string.error_unauthorized_text);
            hasErrors = true;
        }

        @Override
        public void handleUnknownHttpCode(int responseCode, String message) {
            showErrorDialog(hostActivity.getString(R.string.error_unknown_http_code_title),
                            hostActivity.getString(R.string.error_unknown_http_code_text, responseCode + ": " + message));
            if(responseCode >= 400) hasErrors = true;
        }

        @Override
        public void handleRateLimited(String retryAfter) {
            showErrorDialog(R.string.error_too_many_requests_title, R.string.error_too_many_requests_text);
            hasErrors = true;
        }

        @Override
        public void handleBadGateway() {
            showErrorDialog(R.string.error_bad_gateway_title, R.string.error_bad_gateway_text);
            hasErrors = true;
        }

        @Override
        public void handleGatewayTimeout() {
            showErrorDialog(R.string.error_gateway_timeout_title, R.string.error_gateway_timeout_text);
            hasErrors = true;
        }

        @Override
        public void handleEntityTooLarge() {
            showErrorDialog(R.string.error_entity_too_large_title, R.string.error_entity_too_large_text);
            hasErrors = true;
        }

        public void finishedSuccessfully() {
            if(!Network.Status.isOnline()) {
                Network.Status.setOnline();
            }
        }

        public void finishedUnsuccessfully() {
            ;
        }

        @Override
        public void handleIOException(final IOException ex) {
            hostActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(ex instanceof SocketException) {
                        if(Network.Status.checkNetworkStatus(hostActivity))
                            handleSocketException((SocketException)ex);
                        else
                            handleOffline();
                    } else if(ex instanceof FileIOException) {
                        handleFileException((FileIOException)ex);
                    } else {
                        handleUnknownIOException(ex);
                    }
                }
            });
            hasErrors = true;
            hostActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    finishedUnsuccessfully();
                }
            });
        }

        public void handleFileException(FileIOException ex) {
            showErrorDialog(R.string.error_fileex_title, R.string.error_fileex_text);
            Log.e(LOGGING_TAG, "Unexpected FileIOException", ex);
        }

        public void handleOffline() {
            if(Network.Status.isOnline()) {
                showErrorDialog(R.string.error_changed_offline_title, R.string.error_changed_offline_text);
                Network.Status.setOffline();
            }
        }

        public void handleSocketException(SocketException ex) {
            if(Network.Status.isOnline()) { //prevents this dialog from popping up multiple times. Should it?
                showErrorDialog(R.string.error_socketex_title, R.string.error_socketex_text);
                Log.e(LOGGING_TAG, "Unexpected SocketException", ex);
                Network.Status.setOffline(); //todo ?
            }
        }

        public void handleUnknownIOException(IOException ex) {
            showErrorDialog(R.string.error_unknown_ioex_title, R.string.error_unknown_ioex_text);
            Log.e(LOGGING_TAG, "Unexpected unknown IOException", ex);
        }
    }*/
}
