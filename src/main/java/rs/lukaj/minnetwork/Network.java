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

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;


/**
 * Obavlja mrežne zahteve direktno (lowlevel)
 * Created by luka on 1.1.16.
 */
public class Network {
    public static int getBufferSize() {
        return BUFFER_SIZE;
    }

    public static void setBufferSize(int bufferSize) {
        BUFFER_SIZE = bufferSize;
    }

    private static int BUFFER_SIZE = 51_200; //50kb


    /**
     * Network callbacks used for async calls.
     */
    public interface NetworkCallbacks<T> {
        /**
         * Request has completed. Check whether response is okay and handle possible error
         * codes appropriately.
         * @param id request id
         * @param response response
         */
        void onRequestCompleted(int id, Response<T> response);

        /**
         * Exception has been thrown during request execution. Some RuntimeExceptions might
         * not get caught and reported in this fashion, which will make them silently shut
         * down the background thread (keep in mind while debugging).
         * @param id request id
         * @param ex exception thrown
         */
        void onExceptionThrown(int id, Throwable ex);
    }

    /**
     * Holds a response from server. Consists of response code, an (optional) content and (optional) error message.
     * There cannot be both an error message and content present. Consult response codes in order to figure out what
     * to look for, or use {@link #isError()}
     */
    public static class Response<T> {
        //posible response codes
        //normal execution, in most cases can be treated as interchangeable
        public static final int RESPONSE_OK                 = 200;
        public static final int RESPONSE_CREATED            = 201;
        public static final int RESPONSE_ACCEPTED           = 202;
        //HttpURLConnection should handle this almost always
        public static final int NOT_MODIFIED                = 304;
        //client errors
        public static final int RESPONSE_BAD_REQUEST        = 400;
        public static final int RESPONSE_UNAUTHORIZED       = 401;
        public static final int RESPONSE_FORBIDDEN          = 403;
        public static final int RESPONSE_NOT_FOUND          = 404;
        public static final int RESPONSE_DUPLICATE          = 409;
        public static final int RESPONSE_GONE               = 410;
        public static final int RESPONSE_ENTITY_TOO_LARGE   = 413;
        public static final int RESPONSE_TOO_MANY_REQUESTS  = 429;
        //server errors
        public static final int RESPONSE_SERVER_ERROR       = 500;
        public static final int RESPONSE_BAD_GATEWAY        = 502;
        public static final int RESPONSE_SERVER_DOWN        = 503;
        public static final int RESPONSE_GATEWAY_TIMEOUT    = 504;
        public static final int RESPONSE_SERVER_UNREACHABLE = 521;

        private static final String TAG = "Network";
        public final  int        responseCode;
        public final  T          responseData;
        public final  String     errorMessage;
        private final AuthTokenManager tokens;
        private final Request<T> request;

        private Response(Request<T> request, int responseCode, T responseData, String errorMessage,
                         AuthTokenManager tokens) {
            this.request = request;
            this.responseCode = responseCode;
            this.responseData = responseData;
            this.errorMessage = errorMessage;
            this.tokens = tokens;
        }

        public static boolean isError(int code) {
            return code >=RESPONSE_BAD_REQUEST;
        }

        public boolean isError() {
            return isError(responseCode);
        }

        /**
         * Uses passed NetworkExceptionHandler to handle possible errors and returns new response.
         * It is not guaranteed same Response object (this) will be returned, or that no other requests will be made.
         * Should be called on the background thread.
         */
        public Response<T> handleErrorCode(NetworkExceptionHandler handler) {
            if(!isError()) {
                return this;
            }
            switch (responseCode) {
                case RESPONSE_UNAUTHORIZED:
                    if(tokens != null && tokens.getTokenStatus(this) == AuthTokenManager.TOKEN_EXPIRED) {
                        try {
                            tokens.handleTokenError(this, handler); //this should make actual request to refresh token
                            Response<T> handled = request.call(); //redoing the current request
                            handled.handleErrorCode(handler);
                            return handled;
                        } catch (NotLoggedInException ex) {
                            handler.handleUserNotLoggedIn();
                        } catch (IOException e) {
                            handler.handleIOException(e);
                        }
                    } else if(tokens != null && tokens.getTokenStatus(this) == AuthTokenManager.TOKEN_INVALID) {
                        handler.handleUserNotLoggedIn();
                    } else {
                        handler.handleUnauthorized(errorMessage);
                    }
                    break;
                case RESPONSE_FORBIDDEN:
                    handler.handleInsufficientPermissions(errorMessage);
                    return this;
                case RESPONSE_SERVER_ERROR:
                    handler.handleServerError(errorMessage);
                    return this;
                case RESPONSE_NOT_FOUND:
                    handler.handleNotFound(RESPONSE_NOT_FOUND);
                    return this;
                case RESPONSE_GONE:
                    handler.handleNotFound(RESPONSE_GONE);
                    return this;
                case RESPONSE_ENTITY_TOO_LARGE:
                    handler.handleEntityTooLarge();
                    return this;
                case RESPONSE_DUPLICATE:
                    handler.handleDuplicate();
                    return this;
                case RESPONSE_BAD_REQUEST:
                    handler.handleBadRequest(errorMessage);
                    return this;
                case RESPONSE_TOO_MANY_REQUESTS:
                    handler.handleRateLimited(""); //todo read retry-after header
                    return this;
                case RESPONSE_SERVER_UNREACHABLE:
                    handler.handleUnreachable();
                    return this;
                case RESPONSE_BAD_GATEWAY:
                    handler.handleBadGateway();
                    return this;
                case RESPONSE_SERVER_DOWN:
                    if("Maintenance".equals(errorMessage)) handler.handleMaintenance(""); //todo read retry-after header
                    else handler.handleUnreachable();
                    return this;
                case RESPONSE_GATEWAY_TIMEOUT:
                    handler.handleGatewayTimeout();
                    return this;
                default:
                    handler.handleUnknownHttpCode(responseCode, responseData == null ? errorMessage : responseData.toString());
                    return this;
            }
            return this;
        }
    }


    /**
     * Represents one network request, consisting of url, authorization token, and http method
     * (get, post, update, delete). In case of async calls (on executor) it also holds requestId
     * and appropriate callback. T is type of request, i.e. what's sent and what's received.
     * Currently, it's not possible to have StringRequest which has file as a request (though
     * it's of course possible to have empty request, i.e. get, which is usually more appropriate).
     */
    protected static abstract class Request<T> implements Callable<Response<T>> {
        private int                 requestId;
        private URL                 url;
        private AuthTokenManager    tokens;
        private String              httpVerb;
        private NetworkCallbacks<T> callback;

        private Request(int requestId, URL url, AuthTokenManager tokens, String httpVerb,
                        NetworkCallbacks<T> callback) {
            this.requestId = requestId;
            this.url = url;
            this.tokens = tokens;
            this.httpVerb = httpVerb;
            this.callback = callback; //in case this isn't null, it also notifies callback when request is done (apart from 
                                      //returning value in call() method)
        }

        @Override
        public Response<T> call() throws IOException {
            HttpURLConnection conn;
            conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod(httpVerb);
                if (tokens != null && tokens.getToken() != null) {
                    conn.setRequestProperty("Authorization", tokens.getToken());
                }
                uploadData(conn);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (Response.isError(responseCode)) {
                    char[] errorMsg = new char[512];
                    Arrays.fill(errorMsg, '\0');
                    Reader errorReader = new InputStreamReader(conn.getErrorStream());
                    errorReader.read(errorMsg);
                    errorReader.close();
                    conn.disconnect();
                    int end = 0;
                    while (end < 256 && errorMsg[end] != '\0') end++;
                    final Response<T> response = new Response<>(this,
                                                                responseCode,
                                                                null, //no response body, error
                                                                String.valueOf(errorMsg).substring(0, end),
                                                                tokens);
                    if (callback != null) {
                        callback.onRequestCompleted(requestId, response);
                    }
                    return response;
                } else {
                    String encoding = conn.getContentEncoding();
                    final Response<T> response = new Response<>(this,
                                                                responseCode,
                                                                getData(Utils.wrapStream(encoding, conn.getInputStream())),
                                                                null,
                                                                tokens); //no error message, everything's ok
                    conn.disconnect();
                    if (callback != null) {
                        callback.onRequestCompleted(requestId, response);
                    }
                    return response;
                }
            } catch (final Throwable ex) {
                if (callback != null) {
                    callback.onExceptionThrown(requestId, ex);
                    return null;
                } else {
                    throw ex;
                }
            }
        }

        protected abstract T getData(InputStream stream) throws IOException;
        protected abstract void uploadData(URLConnection connection) throws IOException;
    }

    /**
     * Request which uploads data (key-value pairs) in as a urlencoded form
     */
    protected static class StringRequest extends Request<String> {

        private Map<String, String> data;

        public StringRequest(int requestId, URL url, AuthTokenManager tokens, Map<String, String> data,
                             String httpVerb, NetworkCallbacks<String> callback) {
            super(requestId,
                  httpVerb.equalsIgnoreCase("get") ? appendDataToUrl(url, data) : url, //first statement needs to be call to super() constructor
                  tokens,
                  httpVerb,
                  callback);
            this.data = data;
        }

        private static URL appendDataToUrl(URL url, Map<String, String> data) {
            StringBuilder params = dataToString(data);
            if(params.length() == 0) return url;

            if(!url.getFile().contains("?"))
                params.insert(0, "?");
            else
                params.insert(0, "&");
            try {
                return new URL(url.getProtocol(),
                               url.getHost(),
                               url.getPort(),
                               url.getFile() + params.toString());
            } catch (MalformedURLException e) {
                throw new InvalidRequest("MalformedURLException while building get URL; probably I screwed up somewhere", e);
            }
        }

        private static StringBuilder dataToString(Map<String, String> data) {
            if(data.isEmpty()) return new StringBuilder();

            try {
                StringBuilder urlParams = new StringBuilder(data.size() * 16);
                for (Map.Entry<String, String> param : data.entrySet()) {
                    urlParams.append(URLEncoder.encode(param.getKey(), "UTF-8")).append('=')
                             .append(URLEncoder.encode(param.getValue(), "UTF-8")).append('&');
                }
                urlParams.deleteCharAt(urlParams.length() - 1); //trailing &
                return urlParams;
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("utf8 encoding unsupported!"); //shouldn't happen. really
            }
        }

        @Override
        protected String getData(InputStream stream) throws IOException {
            BufferedReader reader      = new BufferedReader(new InputStreamReader(stream));
            String line;
            StringBuilder response = new StringBuilder();
            while((line = reader.readLine()) != null) {
                response.append(line).append('\n');
            }
            reader.close();
            return response.toString();
        }

        @Override
        protected void uploadData(URLConnection conn) throws IOException {
            StringBuilder urlParams = dataToString(data);
            if (urlParams.length() > 0) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Content-Length", String.valueOf(urlParams.length()));
                OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
                writer.write(urlParams.toString());
                writer.close();
            }
        }
    }

    /**
     * Request which uploads and/or downloads a file from server
     */
    protected static class FileRequest extends Request<File> {

        private File saveTo;
        private File data;

        public FileRequest(int requestId, URL url, AuthTokenManager tokens, File data, String httpVerb,
                           NetworkCallbacks<File> callback, File saveTo) {
            super(requestId, url, tokens, httpVerb, callback);
            this.saveTo = saveTo;
            this.data = data;
        }

        @Override
        protected File getData(InputStream stream) throws IOException {
            if(saveTo != null) {
                if(!saveTo.exists() && !saveTo.createNewFile()) throw new IOException("Cannot create new file");
                FileOutputStream out  = new FileOutputStream(saveTo);
                byte[]           buff = new byte[BUFFER_SIZE];
                int              readBytes;
                while ((readBytes = stream.read(buff)) != -1) {
                    out.write(buff, 0, readBytes);
                }
                out.close();
                stream.close();
            }
            return saveTo;
        }

        @Override
        protected void uploadData(URLConnection connection) throws IOException {
            if(data != null) {
                connection.setRequestProperty("Content-Type", "application/octet-stream");
                byte[]          buff = new byte[BUFFER_SIZE];
                int             readBytes;
                OutputStream    out  = connection.getOutputStream();
                FileInputStream in   = new FileInputStream(data);
                while((readBytes = in.read(buff)) != -1) {
                    out.write(buff, 0, readBytes);
                }
                out.close(); in.close();
            }
        }
    }
}
