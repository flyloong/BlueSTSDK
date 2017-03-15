package com.st.BlueSTSDK.Example;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;

import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.sunflower.FlowerCollector;
import com.st.BlueSTSDK.Feature;
import com.st.BlueSTSDK.Features.FeatureAudioADPCM;
import com.st.BlueSTSDK.Features.FeatureAudioADPCMSync;
import com.st.BlueSTSDK.Manager;
import com.st.BlueSTSDK.Node;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static com.st.BlueSTSDK.Example.NodeContainerFragment.NODE_TAG;

/**
 * Created by Administrator on 2017/1/10.
 */
public class AudioActivity extends AppCompatActivity implements View.OnClickListener,CompoundButton.OnCheckedChangeListener{
    boolean mIsRecognizer=false;
    boolean mIsAsr=false;
    boolean mIsPlay=false;
    private Switch mSwitcher_BV_IAT;
    private Switch mSwitcher_BV_ASR;
    private Switch mSwitcher_BV_Play;
    private Switch mSwitcher_BV_Transmit;
    private ImageView mImageViewLED;
    // 用HashMap存储听写结果
    // 语音听写对象
    private SharedPreferences mSharedPreferences;
    private static final String KEY_GRAMMAR_ABNF_ID = "grammar_abnf_id";
    // 云端语法文件
    private String mCloudGrammar = null;
    private SpeechRecognizer mIat;
    private EditText mResultText;
    private EditText mLightText;
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
        setTitle("语音技术由科大讯飞提供");
        String nodeTag = getIntent().getStringExtra(NODE_TAG);
        mNode = Manager.getSharedInstance().getNodeWithTag(nodeTag);
        mImageViewLED=(ImageView)findViewById(R.id.ImageView_LED);
        mSwitcher_BV_IAT=(Switch)findViewById(R.id.Switch_BV_IAT);
        mSwitcher_BV_ASR=(Switch)findViewById(R.id.Switch_BV_ASR);
        mSwitcher_BV_Play=(Switch)findViewById(R.id.Switch_BV_Play);
        mSwitcher_BV_Transmit=(Switch)findViewById(R.id.Switch_BV_Transmit);
        mSwitcher_BV_IAT.setOnCheckedChangeListener(this);
        mSwitcher_BV_ASR.setOnCheckedChangeListener(this);
        mSwitcher_BV_Play.setOnCheckedChangeListener(this);
        mSwitcher_BV_Transmit.setOnCheckedChangeListener(this);

        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mResultText = ((EditText) findViewById(R.id.iat_text));
        mLightText = ((EditText) findViewById(R.id.light_text));
        SpeechUtility.createUtility(AudioActivity.this, "appid=" + "5878e808");
        mSharedPreferences = getSharedPreferences(getPackageName(),	MODE_PRIVATE);
        mCloudGrammar = readFile(this,"grammar_sample.abnf","utf-8");
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
        mSwitcher_BV_Transmit.setChecked(true);
        //mSwitcher_BV_Play.setChecked(true);
    }
    @Override
    protected void onResume() {
        super.onResume();
        FlowerCollector.openPageMode(true);
        FlowerCollector.onResume(this);
    }
    @Override
    protected void onPause() {
        super.onPause();
        FlowerCollector.onPause(this);
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
            case R.id.Switch_BV_IAT:
                if (isChecked) {
                    mSwitcher_BV_ASR.setChecked(false);
                    mSwitcher_BV_Transmit.setChecked(true);
                    mIsRecognizer=true;
                    mResultText.setVisibility(View.VISIBLE);
                    //mResultText.setText("Long Form Automatic Speech Recognition"+"\n");
                    mLightText.setText("Long Form Automatic Speech Recognition");
                    mIsAsr=false;
                    setParamIat();
                    mIat.startListening(mRecognizerListener);
                } else {
                    mResultText.setText(null);
                    mLightText.setText(null);
                    mIsRecognizer=false;
                    mIat.stopListening();
                    break;
                }
                break;
            case R.id.Switch_BV_ASR:
                if (isChecked) {
                    mSwitcher_BV_IAT.setChecked(false);
                    mSwitcher_BV_Transmit.setChecked(true);
                    mIsRecognizer=true;
                    mResultText.setVisibility(View.INVISIBLE);
                    mLightText.setText("Auto Speech Recognize"+ "\n"+"please say “开灯”or“关灯”");
                    mIsAsr=true;
                    setParamAsr();
                    mIat.startListening(mRecognizerListener);
                    mImageViewLED.setImageDrawable(getResources().getDrawable(R.drawable.l_0));
                } else {
                    mIsRecognizer=false;
                    mLightText.setText(null);
                    mResultText.setText(null);
                    mIat.stopListening();
                    mImageViewLED.setImageDrawable(null);
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
           // mIat.stopListening();
            if (mIsRecognizer) {
                mIat.startListening(mRecognizerListener);
            }
        }
        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");

        }

        @Override
        public void onResult(com.iflytek.cloud.RecognizerResult results, boolean isLast) {
            Log.d(TAG, results.getResultString());
            if(mIsAsr){
                String text ;
                text = JsonParser.parseGrammarResult(results.getResultString());
                if(text.equals("开灯")){
                    mImageViewLED.setImageDrawable(getResources().getDrawable(R.drawable.l_1));
                }else if(text.equals("关灯")){
                    mImageViewLED.setImageDrawable(getResources().getDrawable(R.drawable.l_0));
                }else if(text.equals("拍照")) {
                    showTip("拍照");
                    Intent intent = new Intent(AudioActivity.this, CameraActivity.class);
                    startActivity(intent);
//                    Intent intent = new Intent();
//                    // 指定拍照的意图。
//                    intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
//
//                    intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(Environment.getExternalStorageDirectory(),System.currentTimeMillis()+".jpg"))); // 指定保存文件的路径
//                    startActivityForResult(intent, 100);

                }else if(text.equals("茄子")){
                    showTip("茄子");
                    try
                    {
                        String keyCommand = "input keyevent " + KeyEvent.KEYCODE_CAMERA;
                        Runtime runtime = Runtime.getRuntime();
                        Process proc = runtime.exec(keyCommand);
                    }
                    catch (IOException e)
                    {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }else if(text.equals("关闭")){
                    CameraActivity.instance.finish();
                }
                mResultText.append(text);
            }else {
                String text ;
                text = JsonParser.parseIatResult(results.getResultString());
                mResultText.append(text);
            }
            if (isLast) {
                if (mIsRecognizer) {
                    mIat.startListening(mRecognizerListener);
                }
                mResultText.append("\n");
                mResultText.setSelection(mResultText.length());
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

        mSwitcher_BV_Play.setChecked(false);
        mSwitcher_BV_Transmit.setChecked(false);
        mSwitcher_BV_IAT.setChecked(false);
        mSwitcher_BV_ASR.setChecked(false);

        mIat.cancel();
        mIat.destroy();
    }
    private void showTip(final String str) {
        mToast.setText(str);
        mToast.show();
    }
    public void setParamIat() {
        mIat.setParameter(SpeechConstant.PARAMS, null);
//2.设置听写参数，详见《科大讯飞MSC API手册(Android)》SpeechConstant类
        mIat.setParameter(SpeechConstant.VAD_BOS,  "10000");
        mIat.setParameter(SpeechConstant.DOMAIN, "iat");
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin ");
        mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
        mIat.setParameter(SpeechConstant.SAMPLE_RATE, "8000");
    }
    public void setParamAsr(){
        int ret;
        mIat.setParameter(SpeechConstant.PARAMS, null);
        mIat.setParameter(SpeechConstant.VAD_BOS, "10000");
        mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
        mIat.setParameter(SpeechConstant.SAMPLE_RATE, "8000");
        mIat.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        ret = mIat.buildGrammar("abnf", mCloudGrammar , mCloudGrammarListener);
        if (ret != ErrorCode.SUCCESS){
            Log.d(TAG,"语法构建失败,错误码：" + ret);
        }else{
            Log.d(TAG,"语法构建成功");
        }
//3.开始识别,设置引擎类型为云端
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, "cloud");
//设置grammarId
        String grammarId = mSharedPreferences.getString(KEY_GRAMMAR_ABNF_ID, null);
        mIat.setParameter(SpeechConstant.CLOUD_GRAMMAR, grammarId);
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
    private GrammarListener mCloudGrammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if(error == null){
                String grammarID = new String(grammarId);
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                if(!TextUtils.isEmpty(grammarId))
                    editor.putString(KEY_GRAMMAR_ABNF_ID, grammarID);
                editor.commit();
                //showTip("语法构建成功：" + grammarId);
            }else{
                showTip("语法构建失败,错误码：" + error.getErrorCode());
            }
        }
    };
    public static String readFile(Context mContext, String file, String code)
    {
        int len = 0;
        byte []buf = null;
        String result = "";
        try {
            InputStream in = mContext.getAssets().open(file);
            len  = in.available();
            buf = new byte[len];
            in.read(buf, 0, len);

            result = new String(buf,code);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_photo, menu);

        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.menu_start_photo) {
            Intent intent = new Intent(AudioActivity.this, CameraActivity.class);
            startActivity(intent);
        }//else
        return super.onOptionsItemSelected(item);
    }//onOptionsItemSelected

}

