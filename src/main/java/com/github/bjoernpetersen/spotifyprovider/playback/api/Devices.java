package com.github.bjoernpetersen.spotifyprovider.playback.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Devices {

  @JsonProperty
  private List<Device> devices;

  public List<Device> getDevices() {
    return devices;
  }
}
