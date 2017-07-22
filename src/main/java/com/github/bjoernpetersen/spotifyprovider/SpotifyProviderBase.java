package com.github.bjoernpetersen.spotifyprovider;

import com.github.bjoernpetersen.jmusicbot.provider.Provider;
import com.wrapper.spotify.Api;
import javax.annotation.Nonnull;

public interface SpotifyProviderBase extends Provider {

  @Nonnull
  Api getApi();
}
