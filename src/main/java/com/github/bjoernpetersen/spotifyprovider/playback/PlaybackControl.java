package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.spotifyprovider.Token;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequestWithBody;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

class PlaybackControl {

  @Nonnull
  private static final Logger log = Logger.getLogger(PlaybackControl.class.getName());

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BASE_URL = "https://api.spotify.com/v1/me/player";

  @Nonnull
  private final Token token;

  public PlaybackControl(@Nonnull Token token) {
    this.token = token;
  }

  private String getAuthString() {
    return "Bearer " + token.getToken();
  }

  public void play(String songId) throws PlaybackException {
    int status = playImpl(songId);
    if (status == 202) {
      try {
        log.warning("Play did return 202.");
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        throw new PlaybackException("Interrupted while waiting for retry", e);
      }
      status = playImpl(songId);
    }
    if (status != 204) {
      throw new PlaybackException("Status is not 204: " + status);
    }
  }

  public void resume() throws PlaybackException {
    int status = playImpl(null);
    if (status == 202) {
      try {
        log.warning("Play returned 202.");
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        throw new PlaybackException("Interrupted while waiting for retry", e);
      }
      status = playImpl(null);
    }
    if (status != 204) {
      throw new PlaybackException("Status is not 204: " + status);
    }
  }

  private int playImpl(@Nullable String songId) throws PlaybackException {
    HttpRequestWithBody request = Unirest.put(BASE_URL + "/play")
      .header(AUTHORIZATION_HEADER, getAuthString());
    if (songId != null) {
      request.body(new JSONObject().put("uris", new JSONArray().put(toUriString(songId))));
    }

    try {
      return request.asJson().getStatus();
    } catch (UnirestException e) {
      throw new PlaybackException(e);
    }
  }

  public void pause() throws PlaybackException {
    int status = pauseImpl();
    if (status == 202) {
      try {
        log.warning("Pause returned 202.");
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        throw new PlaybackException("Interrupted while waiting for retry", e);
      }
      status = pauseImpl();
    }
    if (status != 204) {
      throw new PlaybackException("Status is not 204: " + status);
    }
  }

  private int pauseImpl() throws PlaybackException {
    try {
      return Unirest.put(BASE_URL + "/pause").header(AUTHORIZATION_HEADER, getAuthString())
        .asJson().getStatus();
    } catch (UnirestException e) {
      throw new PlaybackException(e);
    }
  }

  /**
   * Determines whether this song has probably ended yet.
   *
   * @return whether this song is not playing
   * @throws PlaybackException if any error occurs
   */
  public boolean isNotPlaying(String songId) throws PlaybackException {
    HttpResponse<JsonNode> response;
    try {
      response = Unirest.get(BASE_URL + "/currently-playing")
        .header(AUTHORIZATION_HEADER, getAuthString())
        .asJson();
    } catch (UnirestException e) {
      throw new PlaybackException(e);
    }

    if (response.getStatus() != 200) {
      throw new PlaybackException("Returned status " + response.getStatus());
    }

    final String PROGRESS = "progress_ms";
    final String IS_PLAYING = "is_playing";
    final String ITEM = "item";

    JSONObject body = response.getBody().getObject();
    if (!(body.has(PROGRESS) && body.has(IS_PLAYING) && body.has(ITEM))) {
      throw new PlaybackException("Missing a key");
    }

    if (!body.getBoolean(IS_PLAYING) && body.getLong(PROGRESS) == 0) {
      return true;
    }

    JSONObject item = body.getJSONObject(ITEM);
    String id = item.getString("id");
    return !songId.equals(id);
  }

  private String toUriString(String songId) {
    return "spotify:track:" + songId;
  }
}
