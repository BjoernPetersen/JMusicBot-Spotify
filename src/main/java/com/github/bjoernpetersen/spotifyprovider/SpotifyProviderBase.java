package com.github.bjoernpetersen.spotifyprovider;

import com.github.bjoernpetersen.jmusicbot.config.Config;
import com.github.bjoernpetersen.jmusicbot.provider.Provider;
import com.wrapper.spotify.Api;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;

public interface SpotifyProviderBase extends Provider {

  @Nonnull
  Api getApi();

  @Nonnull
  Config.ReadOnlyStringEntry getMarket();
}
