package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.jmusicbot.playback.PlaybackStateListener.PlaybackState;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

final class PlaybackControl {

  @Nonnull
  private static final Logger log = Logger.getLogger(PlaybackControl.class.getName());

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BASE_URL = "https://api.spotify.com/v1/me/player";
  private static final String DEVICE_ID = "device_id";

  @Nonnull
  private final Token token;

  PlaybackControl(@Nonnull Token token) {
    this.token = token;
  }

  private String getAuthString() {
    return "Bearer " + token.getToken();
  }

  @Nullable
  List<Device> getDevices() {
    List<Device> result = getDevicesImpl();
    if (result != null) {
      return result;
    }

    try {
      log.warning("getDevices returned 202");
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      log.finer("Interrupted during waiting for 202");
      return null;
    }

    return getDevicesImpl();
  }

  @Nullable
  private List<Device> getDevicesImpl() {
    GetRequest request = Unirest.get(BASE_URL + "/devices")
        .header(AUTHORIZATION_HEADER, getAuthString());

    HttpResponse<JsonNode> response;
    try {
      response = request.asJson();
    } catch (UnirestException e) {
      return null;
    }

    if (response.getStatus() != 200) {
      return null;
    }

    JSONArray devices = response.getBody().getObject().getJSONArray("devices");
    return IntStream.range(0, devices.length())
        .mapToObj(devices::getJSONObject)
        .map(d -> new Device(d.getString("id"), d.getString("name")))
        .collect(Collectors.toList());
  }

  public void play(@Nonnull String deviceId, String songId) throws PlaybackException {
    int status = playImpl(deviceId, songId);
    if (status == 202) {
      try {
        log.warning("Play did return 202.");
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        throw new PlaybackException("Interrupted while waiting for retry", e);
      }
      status = playImpl(deviceId, songId);
    }
    if (status != 204) {
      throw new PlaybackException("Status is not 204: " + status);
    }
  }

  public void resume(@Nonnull String deviceId) throws PlaybackException {
    int status = playImpl(deviceId, null);
    if (status == 202) {
      try {
        log.warning("Play returned 202.");
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        throw new PlaybackException("Interrupted while waiting for retry", e);
      }
      status = playImpl(deviceId, null);
    }
    if (status != 204) {
      throw new PlaybackException("Status is not 204: " + status);
    }
  }

  private int playImpl(@Nonnull String deviceId, @Nullable String songId) throws PlaybackException {
    HttpRequestWithBody request = Unirest.put(BASE_URL + "/play")
        .header(AUTHORIZATION_HEADER, getAuthString())
        .queryString(DEVICE_ID, deviceId);

    if (songId != null) {
      request.body(new JSONObject().put("uris", new JSONArray().put(toUriString(songId))));
    }

    try {
      return request.asJson().getStatus();
    } catch (UnirestException e) {
      throw new PlaybackException(e);
    }
  }

  public void pause(@Nonnull String deviceId) throws PlaybackException {
    int status = pauseImpl(deviceId);
    if (status == 202) {
      try {
        log.warning("Pause returned 202.");
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        throw new PlaybackException("Interrupted while waiting for retry", e);
      }
      status = pauseImpl(deviceId);
    }
    if (status != 204) {
      throw new PlaybackException("Status is not 204: " + status);
    }
  }

  private int pauseImpl(@Nonnull String deviceId) throws PlaybackException {
    try {
      return Unirest.put(BASE_URL + "/pause")
          .header(AUTHORIZATION_HEADER, getAuthString())
          .queryString(DEVICE_ID, deviceId)
          .asJson().getStatus();
    } catch (UnirestException e) {
      throw new PlaybackException(e);
    }
  }

  /**
   * Determines whether this song is playing, paused, or stopped/skipped.
   *
   * @return PLAY, PAUSE, or null if the song is not playing anymore
   * @throws PlaybackException if any error occurs
   */
  @Nullable
  public PlaybackState checkState(String songId) throws PlaybackException {
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
      return null;
    }

    JSONObject item = body.getJSONObject(ITEM);
    String id = item.getString("id");
    if (!songId.equals(id)) {
      return null;
    }

    return body.getBoolean(IS_PLAYING) ? PlaybackState.PLAY : PlaybackState.PAUSE;
  }

  private String toUriString(String songId) {
    return "spotify:track:" + songId;
  }
}
