package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.jmusicbot.Loggable;
import com.github.bjoernpetersen.spotifyprovider.playback.TokenRefresher.TokenValues;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

public final class Token implements Loggable {

  @Nonnull
  private final TokenRefresher tokenRefresher;
  @Nonnull
  private String token;
  @Nonnull
  private Date expiration;

  @Nonnull
  private final List<Consumer<Token>> changeListener;

  Token(TokenValues values, TokenRefresher tokenRefresher) {
    this(values.getToken(), values.getExpirationDate(), tokenRefresher);
  }

  Token(@Nonnull String token, @Nonnull Date expiration, TokenRefresher tokenRefresher) {
    this.tokenRefresher = tokenRefresher;
    this.token = token;
    this.expiration = expiration;
    this.changeListener = new LinkedList<>();
  }

  @Nonnull
  public String getToken() {
    if (isExpired()) {
      tryRefresh();
    }
    return token;
  }

  public void addListener(Consumer<Token> listener) {
    changeListener.add(listener);
  }

  public void removeListener(Consumer<Token> listener) {
    changeListener.remove(listener);
  }

  private void tryRefresh() {
    try {
      TokenValues values = tokenRefresher.refresh();
      this.token = values.getToken();
      this.expiration = values.getExpirationDate();
      logFiner("Refreshed access token");
      changeListener.forEach(c -> c.accept(this));
    } catch (IOException e) {
      logSevere(e, "Could not refresh token");
    }
  }

  private boolean isExpired() {
    Date now = new Date();
    return now.after(expiration);
  }
}
