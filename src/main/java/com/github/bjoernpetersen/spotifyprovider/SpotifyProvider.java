package com.github.bjoernpetersen.spotifyprovider;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.Loggable;
import com.github.bjoernpetersen.jmusicbot.PlaybackFactoryManager;
import com.github.bjoernpetersen.jmusicbot.Song;
import com.github.bjoernpetersen.jmusicbot.SongLoader;
import com.github.bjoernpetersen.jmusicbot.config.Config;
import com.github.bjoernpetersen.jmusicbot.config.Config.Entry;
import com.github.bjoernpetersen.jmusicbot.config.ui.TextBox;
import com.github.bjoernpetersen.jmusicbot.platform.Platform;
import com.github.bjoernpetersen.jmusicbot.platform.Support;
import com.github.bjoernpetersen.jmusicbot.playback.PlaybackFactory;
import com.github.bjoernpetersen.jmusicbot.provider.NoSuchSongException;
import com.github.bjoernpetersen.jmusicbot.provider.Provider;
import com.github.bjoernpetersen.spotifyprovider.playback.SpotifyPlaybackFactory;
import com.github.bjoernpetersen.spotifyprovider.playback.Token;
import com.google.common.collect.Lists;
import com.wrapper.spotify.Api;
import com.wrapper.spotify.exceptions.WebApiException;
import com.wrapper.spotify.models.Image;
import com.wrapper.spotify.models.SimpleArtist;
import com.wrapper.spotify.models.Track;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SpotifyProvider implements Loggable, SpotifyProviderBase {

  private Config.ReadOnlyStringEntry market;

  private Token token;
  private Api api;
  private Song.Builder songBuilder;

  @Nonnull
  @Override
  public Config.ReadOnlyStringEntry getMarket() {
    return market;
  }

  @Nonnull
  @Override
  public Class<? extends Provider> getBaseClass() {
    return SpotifyProviderBase.class;
  }

  @Nonnull
  @Override
  public Api getApi() {
    return api;
  }

  @Nonnull
  @Override
  public Support getSupport(@Nonnull Platform platform) {
    switch (platform) {
      case ANDROID:
        // Token retrieval is not supported yet
        return Support.NO;
      case LINUX:
      case WINDOWS:
        return Support.YES;
      case UNKNOWN:
      default:
        return Support.MAYBE;
    }
  }

  @Nonnull
  @Override
  public List<? extends Entry> initializeConfigEntries(@Nonnull Config config) {
    market = config.new StringEntry(
        getClass(),
        "market",
        "Two-letter country code of your Spotify account",
        false,
        "DE",
        new TextBox(),
        countryCode -> {
          if (countryCode.length() != 2) {
            return "Country code must have two letters";
          }
          return null;
        }
    );

    return Collections.singletonList(market);
  }

  @Nonnull
  @Override
  public List<? extends Entry> getMissingConfigEntries() {
    List<Entry> missing = new ArrayList<>(2);
    if (market.checkError() != null) {
      missing.add(market);
    }
    return missing;
  }

  @Override
  public void destructConfigEntries() {
    market.destruct();
    market = null;
  }

  @Override
  public Set<Class<? extends PlaybackFactory>> getPlaybackDependencies() {
    return Collections.singleton(SpotifyPlaybackFactory.class);
  }

  @Override
  public void initialize(@Nonnull InitStateWriter initStateWriter,
      @Nonnull PlaybackFactoryManager manager) throws InitializationException {
    SpotifyPlaybackFactory factory = manager.getFactory(SpotifyPlaybackFactory.class);
    this.token = factory.getToken();
    api = Api.builder()
        .accessToken(token.getToken())
        .build();
    token.addListener(t -> api.setAccessToken(t.getToken()));
    songBuilder = initializeSongBuilder(factory);
  }

  private Song.Builder initializeSongBuilder(SpotifyPlaybackFactory factory) {
    return new Song.Builder()
        .playbackSupplier(song -> factory.getPlayback(song.getId()))
        .songLoader(SongLoader.DUMMY)
        .provider(this);
  }

  @Override
  public void close() throws IOException {
    api = null;
    songBuilder = null;
    token = null;
  }

  @Nonnull
  private Song createSong(String id, String title, String description, int durationMs,
      @Nullable String albumArtUrl) {
    return songBuilder
        .id(id)
        .title(title)
        .description(description)
        .duration(durationMs / 1000)
        .albumArtUrl(albumArtUrl)
        .build();
  }

  @Nonnull
  @Override
  public List<Song> search(@Nonnull String query) {
    if (Objects.requireNonNull(query).isEmpty()) {
      return Collections.emptyList();
    }
    try {
      return api.searchTracks(query)
          .limit(40)
          .market(market.getValue())
          .build().get().getItems().stream()
          .map(this::songFromTrack)
          .collect(Collectors.toList());
    } catch (IOException | WebApiException e) {
      logSevere(e, "Error searching for spotify songs (query: %s)", query);
      return Collections.emptyList();
    }
  }

  @Nonnull
  private Song songFromTrack(@Nonnull Track track) {
    String id = track.getId();
    String title = track.getName();
    String description = track.getArtists().stream()
        .map(SimpleArtist::getName)
        .reduce((l, r) -> l + ", " + r)
        .orElseThrow(() -> new IllegalStateException("Found song without artists"));
    int durationMs = track.getDuration();
    List<Image> images = track.getAlbum().getImages();
    String albumArtUrl = images.isEmpty() ? null : images.get(0).getUrl();
    return createSong(id, title, description, durationMs, albumArtUrl);
  }

  @Nonnull
  @Override
  public Song lookup(@Nonnull String songId) throws NoSuchSongException {
    try {
      return songFromTrack(api.getTrack(songId).build().get());
    } catch (IOException | WebApiException e) {
      throw new NoSuchSongException("Error looking up song: " + songId, e);
    }
  }

  @Nonnull
  @Override
  public List<Song> lookupBatch(@Nonnull List<String> ids) {
    List<Song> result = new ArrayList<>(ids.size());
    for (List<String> subIds : Lists.partition(ids, 50)) {
      try {
        api.getTracks(subIds).build().get().stream()
            .map(this::songFromTrack)
            .forEach(result::add);
      } catch (IOException | WebApiException e) {
        logInfo(e, "Could not lookup IDs.");
      }
    }
    return Collections.unmodifiableList(result);
  }

  @Nonnull
  @Override
  public String getId() {
    return "spotify";
  }

  @Nonnull
  @Override
  public String getReadableName() {
    return "Spotify";
  }
}
