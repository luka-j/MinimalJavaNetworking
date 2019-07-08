# Minimal Java Networking
### with token-based Authorization support

[![](https://jitpack.io/v/luq-0/MinimalJavaNetworking.svg)](https://jitpack.io/#luq-0/MinimalJavaNetworking)


This is the code which I use to connect to the server in my Android apps, factored out in a library.
It doesn't provide any caching (beyond HttpURLConnection default) nor,
well, anything. It is meant to easily and reliably make connections
and communicate with an external service (i.e. REST API)

By default, all requests are made on a background thread, either in a
blocking or async fashion.

For usage instructions, start with NetworkRequestBuilder. It contains
all possible methods to call and make a request.


## Examples
Making a request:
```
NetworkRequestBuilder.create(new URL("http://example.com"), VERB_GET)
                     .id(requestId)
                     .handler(exceptionHandler)
                     .async(callbacks);
```
Or, if uploading a file:
```
File zipFile = new File("example.zip");
NetworkRequestBuilder.create(new URL("http://example.com"), VERB_POST, zipFile)
                     .id(requestId)
                     .auth(TokenManager.getInstance(c))
                     .handler(exceptionHandler)
                     .executor(executor)
                     .async(callbacks);
```
There are multiple `#create(...)` methods which are part of the NetworkRequestBuilder.
If not passing a file to the method, it is implied that you're sending a
`Map<String, String>` as request parameters (possibly `Network.emptyMap`)
and receiving a String. If you're passing a file and parameter map, it is 
implied that you're sending parameters and saving response to a file. If
you're only passing a File, it is implied that you're sending a File and
receiving a String. If you need less opinionated framework, you can use
`Network.Request` and `Network.Response` directly.

Implementing callbacks:
```
public void onRequestCompleted(int requestId, Network.Response<String> response) {
    switch (requestId) {
        case REQUEST_DATA:
            if(response.responseCode == RESPONSE_OK) {
                //parse response.responseData
            }
            //if not using NetworkExceptionHandler, need to handle other response codes here as well
            //otherwise, appropriate method from NEH has already been called
            break;
        case REQUEST_OTHER_DATA:
            if(response.responseCode == RESPONSE_OK) {
                //parse response.responseData
            }
            break;
        default:
            throw new UnsupportedOperationException("invalid request id!");
    }
}
```
Example implementation of NetworkExceptionHandler is given inside the 
source file; as it was written for Android, it requires Android SDK and
has been commented out.