package com.intjinside.myapplication.SoundClient.Receiver;

public class ChunkElement {
    private byte[] buffer;

    public ChunkElement(byte[] buffer) {
        this.buffer = buffer;
    }

    public byte[] getBuffer() {
        return buffer;
    }

}
