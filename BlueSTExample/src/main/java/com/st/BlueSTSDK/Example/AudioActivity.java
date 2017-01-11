package com.st.BlueSTSDK.Example;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.st.BlueSTSDK.Feature;
import com.st.BlueSTSDK.Features.FeatureAudioADPCM;
import com.st.BlueSTSDK.Features.FeatureAudioADPCMSync;
import com.st.BlueSTSDK.Manager;
import com.st.BlueSTSDK.Node;

import static com.st.BlueSTSDK.Example.NodeContainerFragment.NODE_TAG;

/**
 * Created by Administrator on 2017/1/10.
 */
public class AudioActivity extends AppCompatActivity implements View.OnClickListener{
    /**
     * tag used for retrieve the NodeContainerFragment
     */
    private final static String NODE_FRAGMENT = DebugConsoleActivity.class.getCanonicalName() + "" +
            ".NODE_FRAGMENT";
    private AudioTrack mAudioTrack;
    /**
     * node that will stream the data
     */
    private Node mNode;
    /**
     * fragment that manage the node connection and avoid a re connection each time the activity
     * is recreated
     */
    private NodeContainerFragment mNodeContainer;
    /**
     * create an intent for start the activity that will log the information from the node
     *
     * @param c    context used for create the intent
     * @param node note that will be used by the activity
     * @return intent for start this activity
     */
    public static Intent getStartIntent(Context c, @NonNull Node node) {
        Intent i = new Intent(c, AudioActivity.class);
        i.putExtra(NODE_TAG, node.getTag());
        i.putExtras(NodeContainerFragment.prepareArguments(node));
        return i;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        String nodeTag = getIntent().getStringExtra(NODE_TAG);
        mNode = Manager.getSharedInstance().getNodeWithTag(nodeTag);
        findViewById(R.id.Btn_Play).setOnClickListener(this);
        findViewById(R.id.Btn_Stop).setOnClickListener(this);
        //create or recover the NodeContainerFragment
        if (savedInstanceState == null) {
            Intent i = getIntent();
            mNodeContainer = new NodeContainerFragment();
            mNodeContainer.setArguments(i.getExtras());
            getFragmentManager().beginTransaction().add(mNodeContainer, NODE_FRAGMENT).commit();
        } else {
            mNodeContainer = (NodeContainerFragment) getFragmentManager().findFragmentByTag(NODE_FRAGMENT);
        }
        int playBufSize = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if(mAudioTrack==null)
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 8000,AudioFormat.CHANNEL_CONFIGURATION_MONO,  AudioFormat.ENCODING_PCM_16BIT, playBufSize, AudioTrack.MODE_STREAM);
    }
    @Override
    protected void onStart() {
        super.onStart();
        if(!mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCM.class))) {//Ensure the ADPCM is EnableNotification
       mNode.getFeature(FeatureAudioADPCM.class).addFeatureListener(mAudioListener);
        mNode.enableNotification(mNode.getFeature(FeatureAudioADPCM.class));//EnableNotification ADPCM
        }
      if(!mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCMSync.class))) {//Ensure the ADPCMSync is EnableNotification
        mNode.enableNotification(mNode.getFeature(FeatureAudioADPCMSync.class));//EnableNotification ADPCMSync
       }
        mAudioTrack.play();
    }
    @Override
    protected void onStop() {
        synchronized(this) {
            mAudioTrack.pause();
            mAudioTrack.flush();
        }
        if(mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCM.class))) {//Ensure the ADPCM is disableNotification
            mNode.getFeature(FeatureAudioADPCM.class).removeFeatureListener(mAudioListener);
            mNode.disableNotification(mNode.getFeature(FeatureAudioADPCM.class));//disableNotification ADPCM
        }
        if(mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCMSync.class))) {//Ensure the ADPCMSync is EnableNotification
            mNode.disableNotification(mNode.getFeature(FeatureAudioADPCMSync.class));//EnableNotification ADPCMSync
        }
        super.onStop();
    }
    @Override
    public void onBackPressed(){
        mNodeContainer.keepConnectionOpen(true);
        super.onBackPressed();
    }
    @Override
    public   void onClick(View view) {
        switch (view.getId()) {
            case R.id.Btn_Play:
                if(!mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCM.class))) {//Ensure the ADPCM is EnableNotification
                    mNode.getFeature(FeatureAudioADPCM.class).addFeatureListener(mAudioListener);
                    mNode.enableNotification(mNode.getFeature(FeatureAudioADPCM.class));//EnableNotification ADPCM
                }
                if(!mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCMSync.class))) {//Ensure the ADPCMSync is EnableNotification
                    mNode.enableNotification(mNode.getFeature(FeatureAudioADPCMSync.class));//EnableNotification ADPCMSync
                }
                mAudioTrack.play();
                break;
            case R.id.Btn_Stop:
                synchronized(this) {
                    mAudioTrack.pause();
                    mAudioTrack.flush();
                }
                if(mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCM.class))) {//Ensure the ADPCM is disableNotification
                    mNode.getFeature(FeatureAudioADPCM.class).removeFeatureListener(mAudioListener);
                    mNode.disableNotification(mNode.getFeature(FeatureAudioADPCM.class));//disableNotification ADPCM
                }
                if(mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCMSync.class))) {//Ensure the ADPCMSync is EnableNotification
                    mNode.disableNotification(mNode.getFeature(FeatureAudioADPCMSync.class));//EnableNotification ADPCMSync
                }
                break;
        }
    }
    private final Feature.FeatureListener mAudioListener = new Feature.FeatureListener() {
        @Override
        public void onUpdate(final Feature f, final Feature.Sample sample) {
            if(f.getName().equals("AudioFeature")){
                    synchronized (this) {
                        mAudioTrack.write(FeatureAudioADPCM.getAudio(sample), 0, sample.data.length);
                    }
            }
        }
    };
}

