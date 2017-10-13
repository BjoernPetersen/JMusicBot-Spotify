package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.jmusicbot.config.ui.Choice;
import javax.annotation.Nonnull;

final class Device implements Choice<String> {

  private final String id;
  private final String name;

  Device(String id, String name) {
    this.id = id;
    this.name = name;
  }

  @Nonnull
  public String getId() {
    return id;
  }

  @Nonnull
  @Override
  public String getDisplayName() {
    return name;
  }
}
