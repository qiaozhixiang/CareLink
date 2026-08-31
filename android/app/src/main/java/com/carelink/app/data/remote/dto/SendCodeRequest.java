package com.carelink.app.data.remote.dto;

public class SendCodeRequest {
    private String phone;
    public SendCodeRequest(String phone) { this.phone = phone; }
    public String getPhone() { return phone; }
}
