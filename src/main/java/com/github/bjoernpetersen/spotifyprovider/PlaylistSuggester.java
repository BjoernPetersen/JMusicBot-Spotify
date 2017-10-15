package com.github.bjoernpetersen.spotifyprovider;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.Loggable;
import com.github.bjoernpetersen.jmusicbot.Song;
import com.github.bjoernpetersen.jmusicbot.config.Config;
import com.github.bjoernpetersen.jmusicbot.config.Config.Entry;
import com.github.bjoernpetersen.jmusicbot.config.ui.ChoiceBox;
import com.github.bjoernpetersen.jmusicbot.config.ui.ConfigValueConverter;
import com.github.bjoernpetersen.jmusicbot.platform.Platform;
import com.github.bjoernpetersen.jmusicbot.platform.Support;
import com.github.bjoernpetersen.jmusicbot.provider.DependencyMap;
import com.github.bjoernpetersen.jmusicbot.provider.Provider;
import com.github.bjoernpetersen.jmusicbot.provider.Suggester;
import com.github.bjoernpetersen.spotifyprovider.playback.Authenticator;
import com.github.bjoernpetersen.spotifyprovider.playback.Token;
import com.wrapper.spotify.Api;
import com.wrapper.spotify.exceptions.WebApiException;
import com.wrapper.spotify.models.Page;
import com.wrapper.spotify.models.PlaylistTrack;
import com.wrapper.spotify.models.SimplePlaylist;
import com.wrapper.spotify.models.User;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

public final class PlaylistSuggester implements Suggester, Loggable {

  private Config config;
  private Config.StringEntry userId;
  private Config.StringEntry playlistId;
  private Config.StringEntry playlistOwnerId;

  private SpotifyProviderBase provider;
  private Api api;
  private List<Song> playlist;

  private int nextIndex;
  private LinkedList<Song> nextSongs;

  @Nonnull
  @Override
  public Song suggestNext() {
    Song song = getNextSuggestions(1).get(0);
    removeSuggestion(song);
    return song;
  }

  @Nonnull
  @Override
  public List<Song> getNextSuggestions(int maxLength) {
    int startIndex = nextIndex;
    while (nextSongs.size() < Math.min(Math.max(50, maxLength), 10)) {
      // load more suggestions
      nextSongs.add(playlist.get(nextIndex));
      nextIndex = (nextIndex + 1) % playlist.size();
      if (nextIndex == startIndex) {
        // the playlist is shorter than maxLength
        break;
      }
    }
    return Collections.unmodifiableList(nextSongs);
  }

  @Override
  public void removeSuggestion(@Nonnull Song song) {
    nextSongs.remove(song);
  }

  @Override
  public Set<Class<? extends Provider>> getDependencies() {
    return Collections.singleton(SpotifyProviderBase.class);
  }

  @Nonnull
  @Override
  public String getId() {
    return "spotify";
  }

  @Nonnull
  @Override
  public String getReadableName() {
    return "Spotify playlist";
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
    this.config = config;
    userId = config.new StringEntry(
        getClass(),
        "userId",
        "",
        true,
        null
    );
    playlistOwnerId = config.new StringEntry(
        getClass(),
        "playlistOwnerId",
        "",
        false
    );
    playlistId = config.new StringEntry(
        getClass(),
        "playlistId",
        "",
        false,
        null,
        new ChoiceBox<>(this::getPlaylists,
            new ConfigValueConverter<Config.StringEntry, PlaylistChoice.PlaylistId, PlaylistChoice.PlaylistId>() {
              @Override
              public PlaylistChoice.PlaylistId getWithoutDefault(Config.StringEntry stringEntry) {
                String playlistOwnerId = PlaylistSuggester.this.playlistOwnerId
                    .getValueWithoutDefault();
                String playlistId = stringEntry.getValueWithoutDefault();
                if (playlistOwnerId == null || playlistId == null) {
                  return null;
                }
                return new PlaylistChoice.PlaylistId(playlistOwnerId, playlistId);
              }

              @Override
              public PlaylistChoice.PlaylistId getDefault(Config.StringEntry stringEntry) {
                return null;
              }

              @Override
              public PlaylistChoice.PlaylistId getWithDefault(Config.StringEntry stringEntry) {
                return getWithoutDefault(stringEntry);
              }

              @Override
              public void set(Config.StringEntry stringEntry,
                  PlaylistChoice.PlaylistId playlistChoice) {
                if (playlistChoice == null) {
                  stringEntry.set(null);
                  playlistOwnerId.set(null);
                } else {
                  stringEntry.set(playlistChoice.playlistId);
                  playlistOwnerId.set(playlistChoice.userId);
                }
              }
            }, true)
    );
    return Collections.singletonList(playlistId);
  }

  @Nonnull
  @Override
  public List<? extends Entry> getMissingConfigEntries() {
    List<Entry> missing = new ArrayList<>(2);
    if (playlistId.getValue() == null || playlistOwnerId.getValue() == null) {
      missing.add(playlistId);
    }
    return missing;
  }

  @Override
  public void destructConfigEntries() {
    config = null;
    playlistId.destruct();
    userId.destruct();
    playlistId = null;
    userId = null;
  }

  @Override
  public void initialize(@Nonnull InitStateWriter initStateWriter,
      @Nonnull DependencyMap<Provider> dependencies)
      throws InitializationException, InterruptedException {
    provider = dependencies.get(SpotifyProviderBase.class);

    if (userId.getValue() == null) {
      userId.set(loadUserId());
    }

    String ownerId = this.playlistOwnerId.getValue();
    String playlistId = this.playlistId.getValue();
    if (playlistId == null || playlistOwnerId.getValue() == null) {
      throw new InitializationException("No playlist selected");
    }
    playlist = loadPlaylist(ownerId, playlistId);
    nextSongs = new LinkedList<>();
  }

  @Nonnull
  private Api getApi() {
    if (api != null) {
      return api;
    }
    // use the providers API if possible
    if (provider != null) {
      return api = provider.getApi();
    }

    Token token;
    try (Authenticator authenticator = new Authenticator(config)) {
      try {
        token = authenticator.initToken(InitStateWriter.LOG);
      } catch (InitializationException e) {
        logInfo(e, "Could not retrieve token");
        throw new IllegalStateException(e);
      }
    }

    return api = Api.builder().accessToken(token.getToken()).build();
  }

  private List<PlaylistChoice> getPlaylists() {
    String userId = this.userId.getValue();
    if (userId == null) {
      logFinest("No userId set, trying to retrieve it...");
      try {
        this.userId.set(loadUserId());
      } catch (InitializationException e) {
        logInfo("user ID could not be found.");
        return null;
      }
      userId = this.userId.getValue();
    }
    Page<SimplePlaylist> playlists;
    try {
      playlists = getApi()
          .getPlaylistsForUser(userId)
          .limit(50)
          .build().get();
    } catch (IOException | WebApiException | IllegalStateException e) {
      logInfo(e, "Could not retrieve playlists");
      return null;
    }

    return playlists.getItems().stream()
        .map(sp -> new PlaylistChoice(
            new PlaylistChoice.PlaylistId(sp.getOwner().getId(), sp.getId()),
            sp.getName())
        ).collect(Collectors.toList());
  }

  @Nonnull
  private String loadUserId() throws InitializationException {
    User user;
    try {
      user = getApi().getMe().build().get();
    } catch (IOException | WebApiException e) {
      throw new InitializationException("Could not get user ID", e);
    }
    return user.getId();
  }

  private List<Song> loadPlaylist(String ownerId, String playlistId)
      throws InitializationException {
    Page<PlaylistTrack> playlist;
    try {
      playlist = getApi().getPlaylistTracks(ownerId, playlistId)
          .build().get();
    } catch (IOException | WebApiException e) {
      throw new InitializationException("Could not load playlist", e);
    }

    List<PlaylistTrack> tracks = playlist.getItems();
    List<String> ids = tracks.stream().map(t -> t.getTrack().getId()).collect(Collectors.toList());

    // TODO add the full list instead of just the first page (100 tracks). API wrapper limitation.
    return provider.lookupBatch(ids);
  }

  @Override
  public void close() throws IOException {
    provider = null;
    playlist = null;
    nextSongs = null;
  }

}
