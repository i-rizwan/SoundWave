package com.intjinside.myapplication.UI;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.intjinside.myapplication.R;
import com.intjinside.myapplication.SoundClient.Receiver.RecordTask;
import com.intjinside.myapplication.SoundClient.Sender.BufferSoundTask;
import com.intjinside.myapplication.Utils.CallbackSendRec;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;


public class ChatActivity extends AppCompatActivity implements CallbackSendRec {
    private RecyclerView mMessageRecycler;
    private MessageListAdapter mMessageAdapter;
    private LinearLayoutManager mManager;
    private ProgressBar sendingBar;
    private boolean isSending = false;
    private boolean isListening = false;
    private boolean isReceiving = false;
    private BufferSoundTask sendTask = null;
    private RecordTask listenTask = null;
    private String sendText;
    private List<Message> messageList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle(R.string.chat);
        }
        sendingBar = findViewById(R.id.progressBar);
        messageList = new ArrayList<>();
        mMessageRecycler = findViewById(R.id.reyclerview_message_list);
        mMessageAdapter = new MessageListAdapter(messageList);
        mMessageRecycler.setAdapter(mMessageAdapter);
        mManager = new LinearLayoutManager(this);
        mManager.setStackFromEnd(true);
        mMessageRecycler.setLayoutManager(mManager);
    }


    @Override
    protected void onStop() {
        super.onStop();
        //if listening task or sending task are active turn them off and return gui to start state
        if (listenTask != null) {
            stopListening();
            listenTask.setWorkFalse();
        }
        if (sendTask != null) {
            stopSending();
            sendTask.setWorkFalse();
        }
    }

    //Called when message is sending
    public void sendMessage(View view) {
        //If listening task is active, turn it of and return gui to start state
        if (isListening) {
            stopListening();
            if (listenTask != null) {
                listenTask.setWorkFalse();
            }
        }

        //Check if activity is already sending
        if (!isSending) {
            //If its not sending, check if message exists and send (prepare GUI and execute task)
            sendText = ((TextView) findViewById(R.id.edittext_chatbox)).getText().toString();
            if (!sendText.isEmpty() && !sendText.equals(" ")) {
                isSending = true;
                sendingBar.setVisibility(View.VISIBLE);
                sendTask = new BufferSoundTask();
                sendTask.setProgressBar(sendingBar);
                sendTask.setCallbackSR(this);
                ((Button) view).setText(R.string.stop);
                try {
                    byte[] byteText = sendText.getBytes("UTF-8");
                    sendTask.setBuffer(byteText);
                    Integer[] tempArr = getSettingsArguments();
                    sendTask.execute(tempArr);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        } else {
            //If its already sending, stop it
            if (sendTask != null) {
                sendTask.setWorkFalse();
            }
            stopSending();
        }
    }

    //Called when sending task or receiving task have finished work
    @Override
    public void actionDone(int srFlag, String message) {
        //If its sending task and activity is still in sending mode
        if (CallbackSendRec.SEND_ACTION == srFlag && isSending) {
            //Update GUI to initial state
            stopSending();
            String text = ((TextView) findViewById(R.id.edittext_chatbox)).getText().toString();
            //IF text was not changed while sending, clear it
            if (sendText.equals(text)) {
                ((TextView) findViewById(R.id.edittext_chatbox)).setText("");
            }
            ;
            //Update messages view and refresh it
            messageList.add(new Message(sendText, 0));
            mMessageAdapter.notifyDataSetChanged();
            mManager.smoothScrollToPosition(mMessageRecycler, null, mMessageAdapter.getItemCount());
        } else {
            //If its receiving task and activity is still in receiving mode
            if (CallbackSendRec.RECEIVE_ACTION == srFlag && isListening) {
                //Update GUI to initial state
                stopListening();
                //If received message exists put it in database and show it on view
                if (!message.equals("")) {
                    messageList.add(new Message(message, 1));
                    mMessageAdapter.notifyDataSetChanged();
                    mManager.smoothScrollToPosition(mMessageRecycler, null, mMessageAdapter.getItemCount());
                }
            }
        }
    }

    //Called when receiving task starts receiving message
    @Override
    public void receivingSomething() {
        //Update view and flag to show that something is receiving
        messageList.add(new Message("Receiving message...", 2));
        mMessageAdapter.notifyDataSetChanged();
        mManager.smoothScrollToPosition(mMessageRecycler, null, mMessageAdapter.getItemCount());
        isReceiving = true;
    }

    //Called to reset view and flag to initial state from sending state
    private void stopSending() {
        ((Button) findViewById(R.id.button_chatbox_send)).setText(R.string.send);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sendingBar.setProgress(1, true);
        } else {
            sendingBar.setProgress(1);
        }
        sendingBar.setVisibility(View.GONE);
        isSending = false;
    }

    //Called to reset view and flag to initial state from listening state
    private void stopListening() {
        if (isReceiving) {
            messageList.remove(messageList.size() - 1);
            mMessageAdapter.notifyDataSetChanged();
            isReceiving = false;
        }
        ((Button) findViewById(R.id.button_chatbox_listen)).setText(R.string.listen);
        isListening = false;
    }

    //Called to start listening task and update GUI to listening
    private void listen() {
        isListening = true;
        ((Button) findViewById(R.id.button_chatbox_listen)).setText(R.string.stop);
        Integer[] tempArr = getSettingsArguments();
        listenTask = new RecordTask();
        listenTask.setCallbackRet(this);
        listenTask.execute(tempArr);
    }

    //Called on listen button click
    public void listenMessage(View view) {
        //If sending task is active, stop it and update GUI
        if (isSending) {
            stopSending();
            if (sendTask != null) {
                sendTask.setWorkFalse();
            }
        }
        //If its not listening check for mic permission and start listening
        if (!isListening) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, 0);
            } else {
                listen();
            }
        }
        //If its already listening, stop listening and update GUI
        else {
            if (listenTask != null) {
                listenTask.setWorkFalse();
            }
            stopListening();
        }
    }

    //Called when user answers on permission request
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case 0: {
                //If user granted permission on mic, continue with listening
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    listen();
                }
                break;
            }
        }
    }

    //Called to get parameters from settings preferences
    private Integer[] getSettingsArguments() {

        String DEF_START_FREQUENCY = "17500";
        String DEF_END_FREQUENCY = "20000";
        String DEF_BIT_PER_TONE = "4";
        boolean DEF_ENCODING = true;
        boolean DEF_ERROR_DETECTION = true;
        String DEF_ERROR_BYTE_NUM = "4";


        Integer[] intArray = new Integer[6];
        intArray[0] = Integer.parseInt(DEF_START_FREQUENCY);
        intArray[1] = Integer.parseInt(DEF_END_FREQUENCY);
        intArray[2] = Integer.parseInt(DEF_BIT_PER_TONE);
        if (DEF_ENCODING) {
            intArray[3] = 1;
        } else {
            intArray[3] = 0;
        }
        if (DEF_ERROR_DETECTION) {
            intArray[4] = 1;
        } else {
            intArray[4] = 0;
        }
        intArray[5] = Integer.parseInt(DEF_ERROR_BYTE_NUM);
        return intArray;

    }
}
