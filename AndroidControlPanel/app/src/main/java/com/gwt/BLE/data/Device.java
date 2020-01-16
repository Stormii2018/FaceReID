package com.gwt.BLE.data;

public class Device {
    private String name;
    private String address;
    private boolean favourite;

    public Device() { }

    public Device(String name, String address) {
        this.name = name;
        this.address = address;
        this.favourite = false;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public boolean isFavourite() {
        return favourite;
    }
}
