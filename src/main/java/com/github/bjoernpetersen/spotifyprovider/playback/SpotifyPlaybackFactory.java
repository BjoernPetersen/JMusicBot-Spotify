package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.config.Config;
import com.github.bjoernpetersen.jmusicbot.config.Config.Entry;
import com.github.bjoernpetersen.jmusicbot.playback.Playback;
import com.github.bjoernpetersen.jmusicbot.playback.PlaybackFactory;
import com.github.bjoernpetersen.spotifyprovider.Token;
import com.mashape.unirest.http.Unirest;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

public class SpotifyPlaybackFactory implements PlaybackFactory {

  @Nonnull
  @Override
  public List<? extends Entry> initializeConfigEntries(Config config) {
    return Collections.emptyList();
  }

  @Override
  public void destructConfigEntries() {
  }

  @Override
  public void initialize(@Nonnull InitStateWriter initStateWriter) throws InitializationException {
  }

  @Override
  public void close() throws IOException {
    Unirest.shutdown();
  }

  @Nonnull
  @Override
  public Collection<Class<? extends PlaybackFactory>> getBases() {
    return Collections.singleton(SpotifyPlaybackFactory.class);
  }


  @Nonnull
  public Playback getPlayback(Token token, String id) {
    return new SpotifyPlayback(token, id);
  }
}
