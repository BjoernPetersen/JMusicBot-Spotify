package com.github.bjoernpetersen.spotifyprovider.playback.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

public class PlayRequest {

  @JsonProperty
  private List<String> uris;

  public PlayRequest(@Nonnull String uri) {
    this.uris = Collections.singletonList(uri);
  }

  public PlayRequest() {
    uris = Collections.emptyList();
  }
}
