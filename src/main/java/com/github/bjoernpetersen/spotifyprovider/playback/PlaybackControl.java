package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.jmusicbot.playback.PlaybackStateListener.PlaybackState;
import com.github.bjoernpetersen.spotifyprovider.playback.api.Device;
import com.github.bjoernpetersen.spotifyprovider.playback.api.Devices;
import com.github.bjoernpetersen.spotifyprovider.playback.api.PlayRequest;
import com.github.bjoernpetersen.spotifyprovider.playback.api.PlayerState;
import com.github.bjoernpetersen.spotifyprovider.playback.api.Track;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

final class PlaybackControl {

  @Nonnull
  private static final Logger log = Logger.getLogger(PlaybackControl.class.getName());

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BASE_URL = "https://api.spotify.com/v1/me/player/";

  @Nonnull
  private final SpotifyService service;
  @Nonnull
  private final Token token;

  PlaybackControl(@Nonnull Token token) {
    this.token = token;
    OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(chain -> {
          Request request = chain.request();
          request = request.newBuilder()
              .header(AUTHORIZATION_HEADER, getAuthString())
              .build();
          return chain.proceed(request);
        })
        .build();
    service = new Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(JacksonConverterFactory.create())
        .build().create(SpotifyService.class);
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
    Response<Devices> response;
    try {
      response = service.getDevices().execute();
    } catch (IOException e) {
      return null;
    }

    if (response.code() != 200 || response.body() == null) {
      return null;
    }

    return response.body().getDevices();
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
    Call<ResponseBody> call;
    if (songId == null) {
      call = service.resume(deviceId);
    } else {
      call = service.play(deviceId, new PlayRequest(toUriString(songId)));
    }

    try {
      return call.execute().code();
    } catch (IOException e) {
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
      return service.pause(deviceId).execute().code();
    } catch (IOException e) {
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
    Response<PlayerState> response;
    try {
      response = service.getState().execute();
    } catch (IOException e) {
      throw new PlaybackException(e);
    }

    if (response.code() != 200) {
      throw new PlaybackException("Returned status " + response.code());
    }

    PlayerState body = response.body();
    if (body == null) {
      throw new PlaybackException("Invalid body");
    }

    if (!body.isPlaying() && body.getProgress() == 0) {
      return null;
    }

    Track track = body.getTrack();
    String id = track.getId();
    if (!songId.equals(id)) {
      return null;
    }

    return body.isPlaying() ? PlaybackState.PLAY : PlaybackState.PAUSE;
  }

  private String toUriString(String songId) {
    return "spotify:track:" + songId;
  }
}
