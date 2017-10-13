package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.config.Config;
import com.github.bjoernpetersen.jmusicbot.config.Config.StringEntry;
import com.github.bjoernpetersen.jmusicbot.config.ui.ActionButton;
import com.github.bjoernpetersen.jmusicbot.platform.HostServices;
import com.github.bjoernpetersen.spotifyprovider.playback.TokenRefresher.TokenValues;
import com.google.api.client.auth.oauth2.BrowserClientRequestUrl;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

public final class Authenticator implements AutoCloseable {

  private static final String SPOTIFY_URL = " https://accounts.spotify.com/authorize";
  private static final String CLIENT_ID = "902fe6b9a4b6421caf88ee01e809939a";

  private HostServices hostServices;
  private Config.StringEntry accessToken;
  private Config.StringEntry tokenExpiration;

  public Authenticator(Config config) {
    hostServices = config.getHostServices();
    accessToken = config.new StringEntry(
        getClass(),
        "accessToken",
        "OAuth access token",
        true,
        null,
        new ActionButton("Authorize now", () -> {
          try {
            initToken(InitStateWriter.LOG);
            return true;
          } catch (InitializationException e) {
            return false;
          }
        })
    );
    tokenExpiration = config.new StringEntry(
        getClass(),
        "tokenExpiration",
        "Token expiration date",
        true
    );
  }

  public StringEntry getAccessToken() {
    return accessToken;
  }

  @Nonnull
  public Token initToken(@Nonnull InitStateWriter initStateWriter) throws InitializationException {
    try {
      initStateWriter.state("Retrieving OAuth token");
      Token token = initAuth();
      initStateWriter.state("OAuth token received");
      return token;
    } catch (IOException e) {
      throw new InitializationException("Error authorizing", e);
    }
  }

  public boolean hasToken() {
    if (accessToken.getValue() != null && tokenExpiration.getValue() != null) {
      String expirationString = tokenExpiration.getValue();
      long expiration = Long.parseUnsignedLong(expirationString);
      Date expirationDate = new Date(expiration);
      return !new Date().after(expirationDate);
    }
    return false;
  }

  private Token initAuth() throws IOException {
    if (accessToken.getValue() != null && tokenExpiration.getValue() != null) {
      String token = accessToken.getValue();
      String expirationString = tokenExpiration.getValue();
      long expiration = Long.parseUnsignedLong(expirationString);
      Date expirationDate = new Date(expiration);
      Token result = new Token(token, expirationDate, this::authorize);
      // if it's expired, this call will refresh the token
      result.getToken();
      return result;
    } else {
      TokenValues values = authorize();
      return new Token(values, this::authorize);
    }
  }

  private URL getSpotifyUrl(String state, URL redirectUrl) {
    try {
      return new URL(new BrowserClientRequestUrl(SPOTIFY_URL, CLIENT_ID)
          .setState(state)
          .setScopes(Arrays.asList("user-modify-playback-state", "user-read-playback-state"))
          .setRedirectUri(redirectUrl.toExternalForm()).build());
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private TokenValues authorize() throws IOException {
    String state = generateRandomString();
    LocalServerReceiver receiver = new LocalServerReceiver(50336, state);
    URL redirectUrl = receiver.getRedirectUrl();

    URL url = getSpotifyUrl(state, redirectUrl);
    hostServices.openBrowser(url);

    try {
      TokenValues token = receiver.waitForToken(1, TimeUnit.MINUTES);
      if (token == null) {
        throw new IOException("Received null token.");
      }

      accessToken.set(token.getToken());
      tokenExpiration.set(String.valueOf(token.getExpirationDate().getTime()));

      return token;
    } catch (InterruptedException e) {
      throw new IOException("Not authenticated within 1 minute.", e);
    }
  }

  private String generateRandomString() {
    SecureRandom ran = new SecureRandom();
    return Integer.toString(ran.nextInt(Integer.MAX_VALUE));
  }

  @Override
  public void close() {
    accessToken.destruct();
    tokenExpiration.destruct();
    accessToken = null;
    tokenExpiration = null;
    hostServices = null;
  }
}
