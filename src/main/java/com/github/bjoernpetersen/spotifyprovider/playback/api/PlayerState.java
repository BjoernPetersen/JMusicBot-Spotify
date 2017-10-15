package com.github.bjoernpetersen.spotifyprovider.playback.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerState {

  @JsonProperty("progress_ms")
  private long progress;
  @JsonProperty("is_playing")
  private boolean isPlaying;
  @JsonProperty("item")
  private Track track;

  public long getProgress() {
    return progress;
  }

  public boolean isPlaying() {
    return isPlaying;
  }

  public Track getTrack() {
    return track;
  }
}
