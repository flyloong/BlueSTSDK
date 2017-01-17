package com.st.BlueSTSDK.Example;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;
import com.st.BlueSTSDK.Feature;
import com.st.BlueSTSDK.Features.FeatureAudioADPCM;
import com.st.BlueSTSDK.Features.FeatureAudioADPCMSync;
import com.st.BlueSTSDK.Manager;
import com.st.BlueSTSDK.Node;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;

import static com.st.BlueSTSDK.Example.NodeContainerFragment.NODE_TAG;

/**
 * Created by Administrator on 2017/1/10.
 */
public class AudioActivity extends AppCompatActivity implements View.OnClickListener,CompoundButton.OnCheckedChangeListener{
    boolean mIsRecognizer=false;
    boolean mIsPlay=false;
    private Switch mSwitcher_BV_Recognize;
    private Switch mSwitcher_BV_Play;
    private Switch mSwitcher_BV_Transmit;
    // 用HashMap存储听写结果
    // 语音听写对象
    private SpeechRecognizer mIat;
    private EditText mResultText;
    private Toast mToast;
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();
    private static String TAG = AudioActivity.class.getSimpleName();

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
        mSwitcher_BV_Recognize=(Switch)findViewById(R.id.Switch_BV_Recognize);
        mSwitcher_BV_Play=(Switch)findViewById(R.id.Switch_BV_Play);
        mSwitcher_BV_Transmit=(Switch)findViewById(R.id.Switch_BV_Transmit);
        mSwitcher_BV_Recognize.setOnCheckedChangeListener(this);
        mSwitcher_BV_Play.setOnCheckedChangeListener(this);
        mSwitcher_BV_Transmit.setOnCheckedChangeListener(this);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mResultText = ((EditText) findViewById(R.id.iat_text));
        // 注意： appid 必须和下载的SDK保持一致，否则会出现10407错误
        SpeechUtility.createUtility(AudioActivity.this, "appid=" + "5878e808");
        //1.创建SpeechRecognizer对象，第二个参数：本地听写时传InitListener
        mIat= SpeechRecognizer.createRecognizer(AudioActivity.this, mInitListener);
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
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 8000,AudioFormat.CHANNEL_CONFIGURATION_MONO,  AudioFormat.ENCODING_PCM_16BIT, playBufSize, AudioTrack.MODE_STREAM);
    }
    @Override
    protected void onStart() {
        super.onStart();
        mSwitcher_BV_Transmit.setChecked(true);
        mSwitcher_BV_Play.setChecked(true);
    }
    @Override
    protected void onStop() {
        mSwitcher_BV_Play.setChecked(false);
       mSwitcher_BV_Transmit.setChecked(false);
        mSwitcher_BV_Recognize.setChecked(false);
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
               }
    }
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.Switch_BV_Play:
                if (isChecked) {
                    mIsPlay=true;
                mSwitcher_BV_Transmit.setChecked(true);
                    mAudioTrack.play();
                } else {
                    synchronized(this) {
                        mIsPlay=false;
                        mAudioTrack.pause();
                        mAudioTrack.flush();
                    }
                }
                break;
            case R.id.Switch_BV_Recognize:
                if (isChecked) {
                    mSwitcher_BV_Transmit.setChecked(true);
                    mIsRecognizer=true;
                    setParam();
                    mIat.startListening(mRecognizerListener);
                } else {
                    mIsRecognizer=false;
                    mIat.stopListening();
                    break;
                }
                break;
            case R.id.Switch_BV_Transmit:
                if (isChecked) {
                    if(!mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCM.class))) {//Ensure the ADPCM is EnableNotification
                        mNode.getFeature(FeatureAudioADPCM.class).addFeatureListener(mAudioListener);
                        mNode.enableNotification(mNode.getFeature(FeatureAudioADPCM.class));//EnableNotification ADPCM
                    }
                    if(!mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCMSync.class))) {//Ensure the ADPCMSync is EnableNotification
                        mNode.enableNotification(mNode.getFeature(FeatureAudioADPCMSync.class));//EnableNotification ADPCMSync
                    }
                } else {
                    if(mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCM.class))) {//Ensure the ADPCM is disableNotification
                        mNode.getFeature(FeatureAudioADPCM.class).removeFeatureListener(mAudioListener);
                        mNode.disableNotification(mNode.getFeature(FeatureAudioADPCM.class));//disableNotification ADPCM
                    }
                    if(mNode.isEnableNotification(mNode.getFeature(FeatureAudioADPCMSync.class))) {//Ensure the ADPCMSync is EnableNotification
                        mNode.disableNotification(mNode.getFeature(FeatureAudioADPCMSync.class));//EnableNotification ADPCMSync
                    }
                }
                break;
        }
    }
    private final Feature.FeatureListener mAudioListener = new Feature.FeatureListener() {
        @Override
        public void onUpdate(final Feature f, final Feature.Sample sample) {
            short[] audioSample =FeatureAudioADPCM.getAudio(sample);
            BytesTransUtil bytesTransUtil = BytesTransUtil.getInstance();
            byte[] tmpBuf = bytesTransUtil.Shorts2Bytes(audioSample);
            if(f.getName().equals("AudioFeature")){
                    synchronized (this) {
                        if(mIsPlay)
                        mAudioTrack.write(audioSample, 0, audioSample.length);
                        if(mIsRecognizer)
                       mIat.writeAudio(tmpBuf, 0, tmpBuf.length);
                    }
            }
        }
    };
    /**
     * 听写监听器。
     */
    private com.iflytek.cloud.RecognizerListener mRecognizerListener = new com.iflytek.cloud.RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }
        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            // 如果使用本地功能（语记）需要提示用户开启语记的录音权限。
            showTip(error.getPlainDescription(true));
            mIat.stopListening();
        }
        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
            if (mIsRecognizer) {
                mIat.startListening(mRecognizerListener);
            }
        }

        @Override
        public void onResult(com.iflytek.cloud.RecognizerResult results, boolean isLast) {
            Log.d(TAG, results.getResultString());
            printResult(results);
            if (isLast) {
                // TODO 最后的结果
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据："+data.length);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            if (SpeechEvent.EVENT_SESSION_ID == eventType) {
                String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
                Log.d(TAG, "session id =" + sid);
            }
        }
    };
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 退出时释放连接
        mIat.cancel();
        mIat.destroy();
    }
    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }
    private void printResult(com.iflytek.cloud.RecognizerResult results) {
        //  String text = results.getResultString();
        String text = JsonParser.parseIatResult(results.getResultString());
        String sn = null;
        // 读取json结果中的sn字段
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            sn = resultJson.optString("sn");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mIatResults.put(sn, text);
        StringBuffer resultBuffer = new StringBuffer();
        for (String key : mIatResults.keySet()) {
            resultBuffer.append(mIatResults.get(key));
        }
        mResultText.setText(resultBuffer.toString());
        mResultText.setSelection(mResultText.length());
    }
    public void setParam() {
        mIat.setParameter(SpeechConstant.PARAMS, null);
//2.设置听写参数，详见《科大讯飞MSC API手册(Android)》SpeechConstant类
        mIat.setParameter(SpeechConstant.DOMAIN, "iat");
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin ");
        mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
        mIat.setParameter(SpeechConstant.SAMPLE_RATE, "8000");
    }
    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败，错误码：" + code);
            }
        }
    };
}

