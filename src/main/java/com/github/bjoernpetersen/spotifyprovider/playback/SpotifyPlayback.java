package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.jmusicbot.Loggable;
import com.github.bjoernpetersen.jmusicbot.playback.AbstractPlayback;
import com.github.bjoernpetersen.jmusicbot.playback.PlaybackStateListener.PlaybackState;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

final class SpotifyPlayback extends AbstractPlayback implements Loggable {

  @Nonnull
  private final String deviceId;
  @Nonnull
  private final String songId;
  @Nonnull
  private final PlaybackControl control;
  @Nonnull
  private final ScheduledExecutorService stateChecker;

  private boolean isStarted = false;

  SpotifyPlayback(@Nonnull Token token, @Nonnull String deviceId, @Nonnull String songId) {
    this.deviceId = deviceId;
    this.songId = songId;
    this.control = new PlaybackControl(token);
    this.stateChecker = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder()
            .setNameFormat("Spotify-state-checker-%d")
            .build()
    );
    stateChecker.scheduleWithFixedDelay(this::checkState, 2000, 5000, TimeUnit.MILLISECONDS);
  }

  @Override
  public void play() {
    try {
      if (isStarted) {
        control.resume(deviceId);
      } else {
        control.play(deviceId, songId);
        isStarted = true;
      }
    } catch (PlaybackException e) {
      logSevere(e, "Could not play");
    }
  }

  @Override
  public void pause() {
    try {
      control.pause(deviceId);
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
    stateChecker.shutdown();
    if (!stateChecker.awaitTermination(2, TimeUnit.SECONDS)) {
      stateChecker.shutdownNow();
    }

    super.close();
  }
}
