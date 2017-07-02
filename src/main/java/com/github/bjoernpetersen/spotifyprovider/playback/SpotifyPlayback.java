package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.jmusicbot.Loggable;
import com.github.bjoernpetersen.jmusicbot.NamedThreadFactory;
import com.github.bjoernpetersen.jmusicbot.playback.AbstractPlayback;
import com.github.bjoernpetersen.jmusicbot.playback.PlaybackStateListener.PlaybackState;
import com.github.bjoernpetersen.spotifyprovider.Token;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

class SpotifyPlayback extends AbstractPlayback implements Loggable {

  @Nonnull
  private final String songId;
  @Nonnull
  private final PlaybackControl control;
  @Nonnull
  private final ScheduledExecutorService stateChecker;

  private boolean isStarted = false;

  SpotifyPlayback(@Nonnull Token token, @Nonnull String songId) {
    this.songId = songId;
    this.control = new PlaybackControl(token);
    this.stateChecker = Executors
        .newSingleThreadScheduledExecutor(new NamedThreadFactory("Spotify-state-checker"));
    stateChecker.scheduleWithFixedDelay(this::checkState, 2000, 5000, TimeUnit.MILLISECONDS);
  }

  @Override
  public void play() {
    try {
      if (isStarted) {
        control.resume();
      } else {
        control.play(songId);
        isStarted = true;
      }
    } catch (PlaybackException e) {
      logSevere(e, "Could not play");
    }
  }

  @Override
  public void pause() {
    try {
      control.pause();
    } catch (PlaybackException e) {
      logSevere(e, "Could not pause");
    }
  }

  private void checkState() {
    PlaybackState state;
    try {
      state = control.checkState(songId);
    } catch (PlaybackException e) {
      logWarning(e, "Error while state checking");
      return;
    }

    logFinest("Checked for song state: " + state);

    if (state == null) {
      markDone();
      stateChecker.shutdown();
    } else {
      getPlaybackStateListener().ifPresent(listener -> listener.notify(state));
    }
  }

  @Override
  public void close() throws Exception {
    pause();
    stateChecker.shutdownNow();
    super.close();
  }
}
