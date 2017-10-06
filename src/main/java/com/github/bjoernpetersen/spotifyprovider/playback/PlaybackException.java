package com.github.bjoernpetersen.spotifyprovider.playback;

final class PlaybackException extends Exception {

  public PlaybackException() {
  }

  public PlaybackException(String message) {
    super(message);
  }

  public PlaybackException(String message, Throwable cause) {
    super(message, cause);
  }

  public PlaybackException(Throwable cause) {
    super(cause);
  }
}
