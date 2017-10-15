package com.github.bjoernpetersen.spotifyprovider.playback.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.bjoernpetersen.jmusicbot.config.ui.Choice;
import javax.annotation.Nonnull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Device implements Choice<String> {

  @JsonProperty
  private String id;
  @JsonProperty
  private String name;
  @JsonProperty("is_active")
  private boolean isActive;

  @Nonnull
  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return isActive;
  }

  @Nonnull
  @Override
  public String getDisplayName() {
    return getName();
  }
}
