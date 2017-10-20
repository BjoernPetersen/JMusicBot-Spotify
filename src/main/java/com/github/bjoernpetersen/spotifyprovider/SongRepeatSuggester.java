package com.github.bjoernpetersen.spotifyprovider;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.Song;
import com.github.bjoernpetersen.jmusicbot.config.Config;
import com.github.bjoernpetersen.jmusicbot.config.Config.Entry;
import com.github.bjoernpetersen.jmusicbot.config.ui.TextBox;
import com.github.bjoernpetersen.jmusicbot.platform.Platform;
import com.github.bjoernpetersen.jmusicbot.platform.Support;
import com.github.bjoernpetersen.jmusicbot.provider.DependencyMap;
import com.github.bjoernpetersen.jmusicbot.provider.NoSuchSongException;
import com.github.bjoernpetersen.jmusicbot.provider.Provider;
import com.github.bjoernpetersen.jmusicbot.provider.Suggester;
import com.github.zafarkhaja.semver.Version;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SongRepeatSuggester implements Suggester {

  private Config.StringEntry songUrl;
  private Song song;

  @Nonnull
  @Override
  public Song suggestNext() {
    return song;
  }

  @Nonnull
  @Override
  public List<Song> getNextSuggestions(int maxLength) {
    return Collections.singletonList(song);
  }

  @Override
  public void removeSuggestion(@Nonnull Song song) {
  }

  @Nullable
  private String getSongId(@Nonnull String url) {
    try {
      URL parsed = new URL(url);
      String[] pathParts = parsed.getPath().split("/");
      if (pathParts.length != 3) {
        return null;
      }
      return pathParts[2];
    } catch (MalformedURLException e) {
      return null;
    }
  }

  @Override
  public void initialize(@Nonnull InitStateWriter initStateWriter,
      @Nonnull DependencyMap<Provider> dependencies)
      throws InitializationException, InterruptedException {
    SpotifyProviderBase provider = dependencies.get(SpotifyProviderBase.class);
    if (provider == null) {
      throw new InitializationException("Missing dependency");
    }
    try {
      song = provider.lookup(getSongId(songUrl.getValue()));
    } catch (NoSuchSongException e) {
      throw new InitializationException(e);
    }
  }

  @Override
  public Set<Class<? extends Provider>> getDependencies() {
    return Collections.singleton(SpotifyProviderBase.class);
  }

  @Nonnull
  @Override
  public String getId() {
    return "spotifySong";
  }

  @Nonnull
  @Override
  public List<? extends Entry> initializeConfigEntries(@Nonnull Config config) {
    songUrl = config.new StringEntry(
        getClass(),
        "songUrl",
        "A spotify song link",
        false,
        null,
        new TextBox(),
        url -> getSongId(url) == null ? "Invalid URL" : null
    );
    return Collections.singletonList(songUrl);
  }

  @Nonnull
  @Override
  public List<? extends Entry> getMissingConfigEntries() {
    if (songUrl.isNullOrError()) {
      return Collections.singletonList(songUrl);
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public void destructConfigEntries() {
    songUrl.destruct();
    songUrl = null;
  }


  @Nonnull
  @Override
  public String getReadableName() {
    return "Spotify repeater";
  }

  @Nonnull
  @Override
  public Support getSupport(@Nonnull Platform platform) {
    return Support.YES;
  }

  @Nonnull
  @Override
  public Version getMinSupportedVersion() {
    return Version.forIntegers(0, 12, 0);
  }

  @Override
  public void close() throws IOException {
    song = null;
  }
}
