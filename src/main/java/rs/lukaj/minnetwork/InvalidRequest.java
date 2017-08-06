package rs.lukaj.minnetwork;

/**
 * Thrown when request is for any reason invalid. Whichever reason that is,
 * it's this app developer's fault.
 *
 * Created by luka on 4.8.17.
 */

public class InvalidRequest extends RuntimeException {
    public InvalidRequest(String msg) {
        super(msg);
    }

    public InvalidRequest(String msg, Throwable cause) {
        super(msg, cause);
    }
}
