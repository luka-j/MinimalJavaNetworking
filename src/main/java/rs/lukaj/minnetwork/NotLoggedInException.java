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

/**
 * Signalizes user isn't logged in. Stack trace isn't filled for performance reasons.
 * Created by luka on 4.8.17..
 */

public class NotLoggedInException extends Exception {
    public NotLoggedInException() {super();}
    public NotLoggedInException(Class origin, String message) {super("Origin: " + origin.getCanonicalName()
                                                                     +"\nMessage: " + message);}

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
