package com.intjinside.myapplication.SoundClient.Receiver;

import android.os.AsyncTask;
import android.os.Process;

import com.intjinside.myapplication.EncodingAlgorithm.AdaptiveHuffman.AdaptiveHuffmanDecompress;
import com.intjinside.myapplication.EncodingAlgorithm.AdaptiveHuffman.BitInputStream;
import com.intjinside.myapplication.EncodingAlgorithm.FFT.Complex;
import com.intjinside.myapplication.EncodingAlgorithm.FFT.FastFurierTransform;
import com.intjinside.myapplication.EncodingAlgorithm.ReedSolomon.EncoderDecoder;
import com.intjinside.myapplication.Utils.BitFrequencyConverter;
import com.intjinside.myapplication.Utils.ByteArrayParser;
import com.intjinside.myapplication.Utils.CallbackSendRec;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE;

public class RecordTask extends AsyncTask<Integer, Void, Void> implements Callback {

    private int bufferSizeInBytes = 0;
    private boolean work = true;
    private ArrayList<ChunkElement> recordedArray;
    final private String recordedArraySem = "Semaphore";
    private Recorder recorder = null;
    private String myString = "";
    private CallbackSendRec callbackRet;
    private String fileName = null;

    @Override
    protected Void doInBackground(Integer... integers) {
        Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND + THREAD_PRIORITY_MORE_FAVORABLE);
        int StartFrequency = integers[0];
        int EndFrequency = integers[1];
        int BitPerTone = integers[2];
        int Encoding = integers[3];
        int ErrorCheck = integers[4];
        int ErrorCheckByteNum = integers[5];

        recordedArray = new ArrayList<ChunkElement>();
        BitFrequencyConverter bitConverter = new BitFrequencyConverter(StartFrequency, EndFrequency, BitPerTone);
        int HalfPadd = bitConverter.getPadding() / 2;
        int HandshakeStart = bitConverter.getHandshakeStartFreq();
        int HandshakeEnd = bitConverter.getHandshakeEndFreq();
        recorder = new Recorder();
        recorder.setCallback(this);
        recorder.start();

        int listeningStarted = 0;

        int startCounter = 0;

        int endCounter = 0;

        byte[] namePartBArray = null;

        int lastInfo = 2;
        myString = "";

        while (work) {
            ChunkElement tempElem;
            synchronized (recordedArraySem) {
                while (recordedArray.isEmpty()) {
                    try {
                        recordedArraySem.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                tempElem = recordedArray.remove(0);
                recordedArraySem.notifyAll();
            }

            double currNum = calculate(tempElem.getBuffer(), StartFrequency, EndFrequency, HalfPadd);

            if (listeningStarted == 0) {
                if ((currNum > (HandshakeStart - HalfPadd)) && (currNum < (HandshakeStart + HalfPadd))) {
                    startCounter++;
                    if (startCounter >= 2) {
                        listeningStarted = 1;
                        publishProgress();
                    }
                } else {
                    startCounter = 0;
                }
            } else {

                if ((currNum > (HandshakeStart - HalfPadd)) && (currNum < (HandshakeStart + HalfPadd))) {
                    lastInfo = 2;

                    endCounter = 0;
                } else {
                    //Check if its EndHandshakeFrequency
                    if (currNum > (HandshakeEnd - HalfPadd)) {
                        endCounter++;

                        if (endCounter >= 2) {
                            if (fileName != null && namePartBArray == null) {
                                namePartBArray = bitConverter.getAndResetReadBytes();
                                listeningStarted = 0;
                                startCounter = 0;
                                endCounter = 0;
                            } else {
                                setWorkFalse();
                            }
                        }
                    } else {
                        endCounter = 0;
                        if (lastInfo != 0) {
                            lastInfo = 0;
                            bitConverter.calculateBits(currNum);
                        }
                    }
                }
            }
        }

        //Convert received frequencies to bytes
        byte[] readBytes = bitConverter.getAndResetReadBytes();
        try {
            if (ErrorCheck == 1) {
                EncoderDecoder encoder = new EncoderDecoder();
                ByteArrayParser bParser = new ByteArrayParser();
                ArrayList<byte[]> chunks = bParser.divideInto256Chunks(readBytes, ErrorCheckByteNum);
                for (int i = 0; i < chunks.size(); i++) {
                    readBytes = encoder.decodeData(chunks.get(i), ErrorCheckByteNum);
                    bParser.mergeArray(readBytes);
                }
                readBytes = bParser.getAndResetOutputByteArray();
                if (namePartBArray != null) {
                    encoder = new EncoderDecoder();
                    chunks = bParser.divideInto256Chunks(namePartBArray, ErrorCheckByteNum);
                    for (int i = 0; i < chunks.size(); i++) {
                        namePartBArray = encoder.decodeData(chunks.get(i), ErrorCheckByteNum);
                        bParser.mergeArray(namePartBArray);
                    }
                    namePartBArray = bParser.getAndResetOutputByteArray();
                }
            }

            if (Encoding == 1) {
                InputStream in = new ByteArrayInputStream(readBytes);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                AdaptiveHuffmanDecompress.decompress(new BitInputStream(in), out);
                readBytes = out.toByteArray();
                in.close();
                out.close();
                if (namePartBArray != null) {
                    in = new ByteArrayInputStream(namePartBArray);
                    out = new ByteArrayOutputStream();
                    AdaptiveHuffmanDecompress.decompress(new BitInputStream(in), out);
                    namePartBArray = out.toByteArray();
                    in.close();
                    out.close();
                }
            }
            if (namePartBArray == null) {
                myString = new String(readBytes, "UTF-8");
            } else {
                String fileExtension = new String(namePartBArray, "UTF-8");
                int tempCnt = 1;
                boolean tempFlag = true;
                File tempFile = null;
                while (tempFlag) {
                    myString = "receivedFile" + tempCnt + "." + fileExtension;
                    String fullName = fileName + "/" + myString;
                    tempFile = new File(fullName);
                    if (!tempFile.exists()) {
                        tempFlag = false;
                    }
                    tempCnt++;
                }
                tempFile.createNewFile();
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tempFile));
                bos.write(readBytes);
                bos.flush();
                bos.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //Called for calculating frequency with highest amplitude from sound sample
    private double calculate(byte[] buffer, int StartFrequency, int EndFrequency, int HalfPad) {
        int analyzedSize = 1024;
        Complex[] fftTempArray1 = new Complex[analyzedSize];
        int tempI = -1;
        //Convert sound sample from byte to Complex array
        for (int i = 0; i < analyzedSize * 2; i += 2) {
            short buff = buffer[i + 1];
            short buff2 = buffer[i];
            buff = (short) ((buff & 0xFF) << 8);
            buff2 = (short) (buff2 & 0xFF);
            short tempShort = (short) (buff | buff2);
            tempI++;
            fftTempArray1[tempI] = new Complex(tempShort, 0);
        }

        final Complex[] fftArray1 = FastFurierTransform.fft(fftTempArray1);

        int startIndex1 = ((StartFrequency - HalfPad) * (analyzedSize)) / 44100;
        int endIndex1 = ((EndFrequency + HalfPad) * (analyzedSize)) / 44100;

        int max_index1 = startIndex1;
        double max_magnitude1 = (int) fftArray1[max_index1].abs();
        double tempMagnitude;


        for (int i = startIndex1; i < endIndex1; ++i) {
            tempMagnitude = fftArray1[i].abs();
            if (tempMagnitude > max_magnitude1) {
                max_magnitude1 = (int) tempMagnitude;
                max_index1 = i;
            }
        }
        return 44100 * max_index1 / (analyzedSize);

    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (callbackRet != null) {
            callbackRet.actionDone(CallbackSendRec.RECEIVE_ACTION, myString);
        }
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
        if (callbackRet != null) {
            callbackRet.receivingSomething();
        }
    }

    @Override
    public void onBufferAvailable(byte[] buffer) {
        synchronized (recordedArraySem) {
            recordedArray.add(new ChunkElement(buffer));
            recordedArraySem.notifyAll();
            while (recordedArray.size() > 100) {
                try {
                    recordedArraySem.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public void setWorkFalse() {
        if (recorder != null) {
            recorder.stop();
            recorder = null;
        }
        this.work = false;
    }

    @Override
    public void setBufferSize(int size) {
        bufferSizeInBytes = size;
    }

    public void setCallbackRet(CallbackSendRec callbackRet) {
        this.callbackRet = callbackRet;
    }

}