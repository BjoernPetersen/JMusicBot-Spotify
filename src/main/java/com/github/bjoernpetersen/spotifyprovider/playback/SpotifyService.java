package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.spotifyprovider.playback.api.Devices;
import com.github.bjoernpetersen.spotifyprovider.playback.api.PlayRequest;
import com.github.bjoernpetersen.spotifyprovider.playback.api.PlayerState;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Query;

interface SpotifyService {

  @GET("devices")
  Call<Devices> getDevices();

  /**
   * Plays a song.
   *
   * @param deviceId a device ID
   * @param playRequest a play request
   * @return 204 on success, 202 if you should retry.
   */
  @PUT("play")
  Call<ResponseBody> play(@Query("device_id") String deviceId, @Body PlayRequest playRequest);

  /**
   * Resumes the current song.
   *
   * @param deviceId a device ID
   * @return 204 on success, 202 if you should retry.
   */
  @PUT("play")
  Call<ResponseBody> resume(@Query("device_id") String deviceId);

  /**
   * Pauses a song.
   *
   * @param deviceId a device ID
   * @return 204 on success, 202 if you should retry.
   */
  @PUT("pause")
  Call<ResponseBody> pause(@Query("device_id") String deviceId);

  @GET("currently-playing")
  Call<PlayerState> getState();
}
