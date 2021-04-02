package com.intjinside.myapplication.SoundClient.Receiver;


public interface Callback {
    void onBufferAvailable(byte[] buffer);
    void setBufferSize(int size);
}
