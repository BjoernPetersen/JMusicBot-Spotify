package com.github.bjoernpetersen.spotifyprovider.playback;

import com.github.bjoernpetersen.jmusicbot.InitStateWriter;
import com.github.bjoernpetersen.jmusicbot.InitializationException;
import com.github.bjoernpetersen.jmusicbot.config.Config;
import com.github.bjoernpetersen.jmusicbot.config.Config.Entry;
import com.github.bjoernpetersen.jmusicbot.config.ui.ChoiceBox;
import com.github.bjoernpetersen.jmusicbot.config.ui.DefaultStringConverter;
import com.github.bjoernpetersen.jmusicbot.platform.Platform;
import com.github.bjoernpetersen.jmusicbot.platform.Support;
import com.github.bjoernpetersen.jmusicbot.playback.Playback;
import com.github.bjoernpetersen.jmusicbot.playback.PlaybackFactory;
import com.github.bjoernpetersen.spotifyprovider.playback.api.Device;
import com.github.zafarkhaja.semver.Version;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SpotifyPlaybackFactory implements PlaybackFactory {

  private Config config;
  private Token token;
  private Config.ReadOnlyStringEntry device;


  public Token getToken() throws InitializationException {
    initToken(InitStateWriter.LOG);
    return token;
  }

  private void initToken(InitStateWriter initStateWriter)
      throws InitializationException {
    if (token == null) {
      try (Authenticator authenticator = new Authenticator(config)) {
        token = authenticator.initToken(initStateWriter);
      }
    }
  }

  @Nullable
  private List<Device> getDevices() {
    try {
      return new PlaybackControl(getToken()).getDevices();
    } catch (InitializationException e) {
      return null;
    }
  }

  @Nonnull
  @Override
  public String getReadableName() {
    return "Spotify PlaybackFactory";
  }

  @Nonnull
  @Override
  public Version getMinSupportedVersion() {
    return Version.forIntegers(0, 12, 0);
  }

  @Nonnull
  @Override
  public Support getSupport(@Nonnull Platform platform) {
    return platform == Platform.UNKNOWN ? Support.MAYBE : Support.YES;
  }

  @Nonnull
  @Override
  public List<? extends Entry> initializeConfigEntries(@Nonnull Config config) {
    this.config = config;
    device = config.new StringEntry(
        getClass(),
        "deviceId",
        "Spotify device to use",
        false,
        null,
        new ChoiceBox<>(this::getDevices, DefaultStringConverter.INSTANCE, true)
    );
    return Collections.singletonList(device);
  }

  @Nonnull
  @Override
  public List<? extends Entry> getMissingConfigEntries() {
    if (device.isNullOrError()) {
      return ImmutableList.of(device);
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public void destructConfigEntries() {
    config = null;
    device.destruct();
    device = null;
  }

  @Override
  public void initialize(@Nonnull InitStateWriter initStateWriter) throws InitializationException {
    initToken(initStateWriter);
    if (device.getValue() == null) {
      throw new InitializationException("No device selected");
    }
  }

  @Override
  public void close() throws IOException {
  }

  @Nonnull
  @Override
  public Collection<Class<? extends PlaybackFactory>> getBases() {
    return Collections.singleton(SpotifyPlaybackFactory.class);
  }

  @Nonnull
  public Playback getPlayback(String songId) {
    return new SpotifyPlayback(token, device.getValue(), songId);
  }
}
