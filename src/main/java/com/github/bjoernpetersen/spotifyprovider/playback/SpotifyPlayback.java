package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.jmusicbot.NamedThreadFactory;
import com.github.bjoernpetersen.jmusicbot.playback.AbstractPlayback;
import com.github.bjoernpetersen.spotifyprovider.Token;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

class SpotifyPlayback extends AbstractPlayback {

  @Nonnull
  private static final Logger log = Logger.getLogger(SpotifyPlayback.class.getName());

  @Nonnull
  private final String songId;
  @Nonnull
  private final PlaybackControl control;
  @Nonnull
  private final ScheduledExecutorService doneChecker;

  private boolean isStarted = false;

  SpotifyPlayback(@Nonnull Token token, @Nonnull String songId) {
    this.songId = songId;
    this.control = new PlaybackControl(token);
    this.doneChecker = Executors
        .newSingleThreadScheduledExecutor(new NamedThreadFactory("Spotify-done-checker"));
    doneChecker.scheduleWithFixedDelay(this::checkDone, 2000, 5000, TimeUnit.MILLISECONDS);
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
      log.severe("Could not play: " + e);
    }
  }

  @Override
  public void pause() {
    try {
      control.pause();
    } catch (PlaybackException e) {
      log.severe("Could not pause: " + e);
    }
  }

  private void checkDone() {
    boolean notPlaying;
    try {
      notPlaying = control.isNotPlaying(songId);
    } catch (PlaybackException e) {
      log.warning("Error while done checking: " + e);
      return;
    }

    log.finest("Checked for song done: " + notPlaying);

    if (notPlaying) {
      markDone();
      doneChecker.shutdown();
    }
  }

  @Override
  public void close() throws Exception {
    pause();
    doneChecker.shutdownNow();
    super.close();
  }
}
