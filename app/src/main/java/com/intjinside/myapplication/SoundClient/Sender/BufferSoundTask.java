package com.intjinside.myapplication.SoundClient.Sender;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.ProgressBar;

import com.intjinside.myapplication.EncodingAlgorithm.AdaptiveHuffman.AdaptiveHuffmanCompress;
import com.intjinside.myapplication.EncodingAlgorithm.AdaptiveHuffman.BitOutputStream;
import com.intjinside.myapplication.EncodingAlgorithm.ReedSolomon.EncoderDecoder;
import com.intjinside.myapplication.Utils.BitFrequencyConverter;
import com.intjinside.myapplication.Utils.ByteArrayParser;
import com.intjinside.myapplication.Utils.CallbackSendRec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class BufferSoundTask extends AsyncTask<Integer, Integer, Void> {


    private boolean work = true;
    private double durationSec = 0.270;
    private int sampleRate = 44100;
    private AudioTrack myTone = null;
    private byte[] message;
    private byte[] messageFile;
    private ProgressBar progressBar = null;
    private CallbackSendRec callbackSR;

    @Override
    protected Void doInBackground(Integer... integers) {
        int startFreq = integers[0];
        int endFreq = integers[1];
        int bitsPerTone = integers[2];
        int encoding = integers[3];
        int errorDet = integers[4];
        int errorDetBNum = integers[5];
        BitFrequencyConverter bitConverter = new BitFrequencyConverter(startFreq, endFreq, bitsPerTone);
        byte[] encodedMessage = message;
        byte[] encodedMessageFile = messageFile;

        if (encoding == 1) {
            InputStream in = new ByteArrayInputStream(encodedMessage);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BitOutputStream bitOut = new BitOutputStream(out);
            try {
                AdaptiveHuffmanCompress.compress(in, bitOut);
                bitOut.close();
                encodedMessage = out.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } finally {
                try {
                    in.close();
                    out.close();
                    bitOut.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (encodedMessageFile != null) {
                in = new ByteArrayInputStream(encodedMessageFile);
                out = new ByteArrayOutputStream();
                bitOut = new BitOutputStream(out);
                try {
                    AdaptiveHuffmanCompress.compress(in, bitOut);
                    bitOut.close();
                    encodedMessageFile = out.toByteArray();
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                } finally {
                    try {
                        in.close();
                        out.close();
                        bitOut.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (errorDet == 1) {
            ByteArrayParser bParser = new ByteArrayParser();
            List<byte[]> tempList = bParser.divideInto256Chunks(encodedMessage, errorDetBNum);
            EncoderDecoder encoder = new EncoderDecoder();
            for (int i = 0; i < tempList.size(); i++) {
                try {
                    byte[] tempArr = encoder.encodeData(tempList.get(i), errorDetBNum);
                    bParser.mergeArray(tempArr);
                } catch (EncoderDecoder.DataTooLargeException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            encodedMessage = bParser.getAndResetOutputByteArray();
            if (encodedMessageFile != null) {
                tempList = bParser.divideInto256Chunks(encodedMessageFile, errorDetBNum);
                encoder = new EncoderDecoder();

                for (int i = 0; i < tempList.size(); i++) {
                    try {
                        byte[] tempArr = encoder.encodeData(tempList.get(i), errorDetBNum);
                        bParser.mergeArray(tempArr);
                    } catch (EncoderDecoder.DataTooLargeException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
                encodedMessageFile = bParser.getAndResetOutputByteArray();
            }
        }
        if (encodedMessage == null) {
            return null;
        }
        ArrayList<Integer> freqs = bitConverter.calculateFrequency(encodedMessage);
        ArrayList<Integer> freqsFile = null;
        if (encodedMessageFile != null) {
            freqsFile = bitConverter.calculateFrequency(encodedMessageFile);
        }
        if (!work) {
            return null;
        }
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        myTone = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize,
                AudioTrack.MODE_STREAM);
        myTone.play();
        int currProgress = 0;
        int allLength = freqs.size() * 2 + 4;
        if (freqsFile != null) {
            allLength += freqsFile.size() * 2 + 4;
        }
        playTone((double) bitConverter.getHandshakeStartFreq(), durationSec);
        publishProgress(((++currProgress) * 100) / allLength);
        playTone((double) bitConverter.getHandshakeStartFreq(), durationSec);
        publishProgress(((++currProgress) * 100) / allLength);

        for (int freq : freqs) {
            playTone((double) freq, durationSec / 2);
            publishProgress(((++currProgress) * 100) / allLength);
            playTone((double) bitConverter.getHandshakeStartFreq(), durationSec);
            publishProgress(((++currProgress) * 100) / allLength);
            if (!work) {
                myTone.release();
                return null;
            }
        }
        playTone((double) bitConverter.getHandshakeEndFreq(), durationSec);
        publishProgress(((++currProgress) * 100) / allLength);
        playTone((double) bitConverter.getHandshakeEndFreq(), durationSec);
        publishProgress(((++currProgress) * 100) / allLength);
        if (freqsFile != null) {
            playTone((double) bitConverter.getHandshakeStartFreq(), durationSec);
            publishProgress(((++currProgress) * 100) / allLength);
            playTone((double) bitConverter.getHandshakeStartFreq(), durationSec);
            publishProgress(((++currProgress) * 100) / allLength);
            for (int freq : freqsFile) {
                playTone((double) freq, durationSec / 2);
                publishProgress(((++currProgress) * 100) / allLength);
                playTone((double) bitConverter.getHandshakeStartFreq(), durationSec);
                publishProgress(((++currProgress) * 100) / allLength);
                if (!work) {
                    myTone.release();
                    return null;
                }
            }
            playTone((double) bitConverter.getHandshakeEndFreq(), durationSec);
            publishProgress(((++currProgress) * 100) / allLength);
            playTone((double) bitConverter.getHandshakeEndFreq(), durationSec);
            publishProgress(((++currProgress) * 100) / allLength);
        }
        myTone.release();
        return null;
    }

    @Override
    protected void onProgressUpdate(Integer... integers) {
        super.onProgressUpdate(integers);
        if (progressBar != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                progressBar.setProgress(integers[0], true);
            } else {
                progressBar.setProgress(integers[0]);
            }
        }
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (callbackSR != null) {
            callbackSR.actionDone(CallbackSendRec.SEND_ACTION, null);
        }
    }

    public void playTone(double freqOfTone, double duration) {
        //Calculate number of samples in given duration
        double dnumSamples = duration * sampleRate;
        dnumSamples = Math.ceil(dnumSamples);
        int numSamples = (int) dnumSamples;
        double sample[] = new double[numSamples];
        //Every sample 16bit
        byte generatedSnd[] = new byte[2 * numSamples];
        //Fill the sample array with sin of given frequency
        double anglePadding = (freqOfTone * 2 * Math.PI) / (sampleRate);
        double angleCurrent = 0;
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(angleCurrent);
            angleCurrent += anglePadding;
        }

        int idx = 0;
        int i = 0;
        //Amplitude ramp as a percent of sample count
        int ramp = numSamples / 20;
        //Ramp amplitude up (to avoid clicks)
        for (i = 0; i < ramp; ++i) {
            double dVal = sample[i];
            //Ramp up to maximum
            final short val = (short) ((dVal * 32767 * i / ramp));
            //In 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        // Max amplitude for most of the samples
        for (i = i; i < numSamples - ramp; ++i) {
            double dVal = sample[i];
            //Scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            //In 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        //Ramp amplitude down
        for (i = i; i < numSamples; ++i) {
            double dVal = sample[i];
            //Ramp down to zero
            final short val = (short) ((dVal * 32767 * (numSamples - i) / ramp));
            //In 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        try {
            // Play the track
            myTone.write(generatedSnd, 0, generatedSnd.length);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setBuffer(byte[] message) {
        this.message = message;
    }

    public void setProgressBar(ProgressBar progressBar) {
        this.progressBar = progressBar;
    }

    public void setCallbackSR(CallbackSendRec callbackSR) {
        this.callbackSR = callbackSR;
    }

    public void setWorkFalse() {
        this.work = false;
    }
}
