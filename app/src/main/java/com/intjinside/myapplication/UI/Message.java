package com.intjinside.myapplication.UI;

public class Message {
    private String message;
    private int user;

    public Message(String message, int user) {
        this.message = message;
        this.user = user;
    }

    public int getUser() {
        return user;
    }

    public void setUser(int user) {
        this.user = user;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
