package com.github.bjoernpetersen.spotifyprovider;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.Loggable;
import com.github.bjoernpetersen.jmusicbot.PlaybackFactoryManager;
import com.github.bjoernpetersen.jmusicbot.Song;
import com.github.bjoernpetersen.jmusicbot.SongLoader;
import com.github.bjoernpetersen.jmusicbot.config.Config;
import com.github.bjoernpetersen.jmusicbot.config.Config.Entry;
import com.github.bjoernpetersen.jmusicbot.config.ui.DefaultStringConverter;
import com.github.bjoernpetersen.jmusicbot.config.ui.TextBox;
import com.github.bjoernpetersen.jmusicbot.platform.Platform;
import com.github.bjoernpetersen.jmusicbot.platform.Support;
import com.github.bjoernpetersen.jmusicbot.playback.PlaybackFactory;
import com.github.bjoernpetersen.jmusicbot.provider.NoSuchSongException;
import com.github.bjoernpetersen.jmusicbot.provider.Provider;
import com.github.bjoernpetersen.spotifyprovider.TokenRefresher.TokenValues;
import com.github.bjoernpetersen.spotifyprovider.playback.SpotifyPlaybackFactory;
import com.google.api.client.auth.oauth2.BrowserClientRequestUrl;
import com.wrapper.spotify.Api;
import com.wrapper.spotify.exceptions.WebApiException;
import com.wrapper.spotify.models.Image;
import com.wrapper.spotify.models.SimpleArtist;
import com.wrapper.spotify.models.Track;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SpotifyProvider implements Loggable, SpotifyProviderBase {

  private static final String SPOTIFY_URL = " https://accounts.spotify.com/authorize";
  private static final String CLIENT_ID = "902fe6b9a4b6421caf88ee01e809939a";

  private Config.StringEntry accessToken;
  private Config.StringEntry tokenExpiration;
  private Config.ReadOnlyStringEntry market;

  private Token token;
  private Api api;
  private Song.Builder songBuilder;

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
    accessToken = config.new StringEntry(getClass(), "accessToken", "OAuth access token", true);
    tokenExpiration = config.new StringEntry(
        getClass(),
        "tokenExpiration",
        "Token expiration date",
        true
    );
    market = config.new StringEntry(
        getClass(),
        "market",
        "Two-letter country code of your Spotify account",
        false,
        "DE",
        new TextBox<>(DefaultStringConverter.INSTANCE),
        countryCode -> {
          if (countryCode.length() != 2) {
            return "Country code must have two letters";
          }
          return null;
        }
    );
    return Collections.singletonList(market);
  }

  @Override
  public void dereferenceConfigEntries() {
    accessToken.destruct();
    tokenExpiration.destruct();
    market.destruct();

    accessToken = null;
    tokenExpiration = null;
    market = null;
  }

  @Override
  public Set<Class<? extends PlaybackFactory>> getPlaybackDependencies() {
    return Collections.singleton(SpotifyPlaybackFactory.class);
  }

  @Override
  public void initialize(@Nonnull InitStateWriter initStateWriter,
      @Nonnull PlaybackFactoryManager manager) throws InitializationException {
    try {
      initStateWriter.state("Retrieving OAuth token");
      this.token = initAuth();
      initStateWriter.state("OAuth token received");
    } catch (IOException e) {
      throw new InitializationException("Error authorizing", e);
    }

    api = Api.builder()
        .accessToken(token.getToken())
        .build();
    token.addListener(t -> api.setAccessToken(t.getToken()));

    SpotifyPlaybackFactory factory = manager.getFactory(SpotifyPlaybackFactory.class);
    songBuilder = initializeSongBuilder(factory);
  }

  private Song.Builder initializeSongBuilder(SpotifyPlaybackFactory factory) {
    return new Song.Builder()
        .playbackSupplier(song -> factory.getPlayback(token, song.getId()))
        .songLoader(SongLoader.DUMMY)
        .provider(this);
  }

  @Override
  public void close() throws IOException {
    api = null;
    token = null;
    songBuilder = null;
  }

  private Token initAuth() throws IOException {
    if (accessToken.getValue() != null && tokenExpiration.getValue() != null) {
      String token = accessToken.getValue();
      String expirationString = tokenExpiration.getValue();
      long expiration = Long.parseUnsignedLong(expirationString);
      Date expirationDate = new Date(expiration);
      Token result = new Token(token, expirationDate, this::authorize);
      // if it's expired, this call will refresh the token
      result.getToken();
      return result;
    } else {
      TokenValues values = authorize();
      return new Token(values, this::authorize);
    }
  }

  private TokenValues authorize() throws IOException {
    String state = generateRandomString();
    LocalServerReceiver receiver = null;
    receiver = new LocalServerReceiver(50336, state);

    URL redirectUrl = receiver.getRedirectUrl();

    BrowserClientRequestUrl url = new BrowserClientRequestUrl(SPOTIFY_URL, CLIENT_ID)
        .setState(state)
        .setScopes(Arrays.asList("user-modify-playback-state", "user-read-playback-state"))
        .setRedirectUri(redirectUrl.toExternalForm());

    try {
      Desktop.getDesktop().browse(new URL(url.build()).toURI());
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }

    try {
      TokenValues token = receiver.waitForToken(1, TimeUnit.MINUTES);
      if (token == null) {
        throw new IOException("Received null token.");
      }

      accessToken.set(token.getToken());
      tokenExpiration.set(String.valueOf(token.getExpirationDate().getTime()));

      return token;
    } catch (InterruptedException e) {
      throw new IOException("Not authenticated within 1 minute.", e);
    }
  }

  private String generateRandomString() {
    SecureRandom ran = new SecureRandom();
    return Integer.toString(ran.nextInt(Integer.MAX_VALUE));
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
  public String getId() {
    return "spotify";
  }

  @Nonnull
  @Override
  public String getReadableName() {
    return "Spotify";
  }
}
