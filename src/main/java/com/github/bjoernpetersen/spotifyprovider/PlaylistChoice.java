package com.github.bjoernpetersen.spotifyprovider;

import com.github.bjoernpetersen.jmusicbot.config.ui.Choice;
import com.github.bjoernpetersen.spotifyprovider.PlaylistChoice.PlaylistId;
import javax.annotation.Nonnull;

final class PlaylistChoice implements Choice<PlaylistId> {

  @Nonnull
  private final PlaylistId id;
  @Nonnull
  private final String name;

  PlaylistChoice(@Nonnull PlaylistId id, @Nonnull String name) {
    this.id = id;
    this.name = name;
  }

  @Nonnull
  @Override
  public PlaylistId getId() {
    return id;
  }

  @Nonnull
  @Override
  public String getDisplayName() {
    return name;
  }

  static class PlaylistId {

    @Nonnull
    public final String userId;
    @Nonnull
    public final String playlistId;

    PlaylistId(@Nonnull String userId, @Nonnull String playlistId) {
      this.userId = userId;
      this.playlistId = playlistId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      PlaylistId that = (PlaylistId) o;

      if (!userId.equals(that.userId)) {
        return false;
      }
      return playlistId.equals(that.playlistId);
    }

    @Override
    public int hashCode() {
      int result = userId.hashCode();
      result = 31 * result + playlistId.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return userId + ";" + playlistId;
    }
  }
}
