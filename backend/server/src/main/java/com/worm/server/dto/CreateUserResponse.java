package com.worm.server.dto;

public class CreateUserResponse {
    private String id;
    private String nickname;

    public CreateUserResponse() {
    }

    public CreateUserResponse(String id, String nickname) {
        this.id = id;
        this.nickname = nickname;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}

