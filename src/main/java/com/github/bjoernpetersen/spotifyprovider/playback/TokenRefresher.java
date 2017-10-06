package com.github.bjoernpetersen.spotifyprovider.playback;

import java.io.IOException;
import java.util.Date;
import javax.annotation.Nonnull;

@FunctionalInterface
interface TokenRefresher {

  TokenValues refresh() throws IOException;

  class TokenValues {

    @Nonnull
    private final String token;
    @Nonnull
    private final Date expirationDate;

    TokenValues(@Nonnull String token, @Nonnull Date expirationDate) {
      this.token = token;
      this.expirationDate = expirationDate;
    }

    @Nonnull
    public String getToken() {
      return token;
    }

    @Nonnull
    public Date getExpirationDate() {
      return expirationDate;
    }
  }
}
