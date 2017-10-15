package com.github.bjoernpetersen.spotifyprovider.playback.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Track {

  @JsonProperty
  private String id;

  public String getId() {
    return id;
  }
}
