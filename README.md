# Minimal Java Networking
#### with token-based Authorization support

This is the code which I use to connect to the server in my Android apps, factored out in a library.
It doesn't provide any caching (beyond HttpURLConnection default) nor,
well, anything. It is meant to easily and reliably make connections
and communicate with an external service (i.e. REST API)

By default, all requests are made on a background thread, either in a
blocking or async fashion.