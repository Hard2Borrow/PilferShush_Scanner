package cityfreqs.com.pilfershush.assist;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.util.Log;

import cityfreqs.com.pilfershush.MainActivity;
import cityfreqs.com.pilfershush.R;

public class AudioChecker {
    private static final String TAG = "PilferShush_AUDIO";
    private Context context;
    private Bundle audioBundle;
    private int channelInCount;
    private boolean DEBUG;

    public AudioChecker(Context context, Bundle audioBundle) {
        this.context = context;
        this.audioBundle = audioBundle;
        DEBUG = audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15], false);
    }

    public Bundle getAudioBundle() {
        return audioBundle;
    }

    private int getClosestPowersHigh(int reported) {
        // return the next highest power from the minimum reported
        // 512, 1024, 2048, 4096, 8192, 16384
        for (int power : AudioSettings.POWERS_TWO_HIGH) {
            if (reported <= power) {
                return power;
            }
        }
        // didn't find power, return reported
        return reported;
    }

    public boolean determineRecordAudioType() {
        // guaranteed default for Android is 44.1kHz, PCM_16BIT, CHANNEL_IN_DEFAULT
        /*
        AudioRecord.cpp (samsung fork?)::
        if (inputSource == AUDIO_SOURCE_DEFAULT) {
            inputSource = AUDIO_SOURCE_MIC;
        }
        */
        // test change to audio source:: AUDIO_SOURCE_VOICE_COMMUNICATION (7)
        // FOR PRIORITY BUMP IN ANDROID 10 (API29)
        int audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;// 7 //.DEFAULT; // 0

        // note::
        /*
        media/libstagefright/AudioSource.cpp
        typedef enum {
                    AUDIO_SOURCE_DEFAULT             = 0,
                    AUDIO_SOURCE_MIC                 = 1,
                    AUDIO_SOURCE_VOICE_UPLINK        = 2,  // system only, requires Manifest.permission#CAPTURE_AUDIO_OUTPUT
                    AUDIO_SOURCE_VOICE_DOWNLINK      = 3,  // system only, requires Manifest.permission#CAPTURE_AUDIO_OUTPUT
                    AUDIO_SOURCE_VOICE_CALL          = 4,  // system only, requires Manifest.permission#CAPTURE_AUDIO_OUTPUT
                    AUDIO_SOURCE_CAMCORDER           = 5,  // for video recording, same orientation as camera
                    AUDIO_SOURCE_VOICE_RECOGNITION   = 6,  // tuned for voice recognition
                    AUDIO_SOURCE_VOICE_COMMUNICATION = 7,  // tuned for VoIP with echo cancel, auto gain ctrl if available
                    AUDIO_SOURCE_CNT,
                    AUDIO_SOURCE_MAX                 = AUDIO_SOURCE_CNT - 1,
        } audio_source_t;
        */
        // some pre-processing like echo cancellation, noise suppression is applied on the audio captured using VOICE_COMMUNICATION
        // assumption is that # 6,7 add DSP to the DEFAULT/MIC input

        for (int rate : AudioSettings.SAMPLE_RATES) {
            for (short audioFormat : new short[] {
                    AudioFormat.ENCODING_PCM_16BIT,
                    AudioFormat.ENCODING_PCM_8BIT}) {

                for (short channelInConfig : new short[] {
                        AudioFormat.CHANNEL_IN_DEFAULT, // 1 - switched by OS, not native?
                        AudioFormat.CHANNEL_IN_MONO,    // 16, also CHANNEL_IN_FRONT == 16
                        AudioFormat.CHANNEL_IN_STEREO }) {  // 12
                    try {
                        if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15])) {
                            debugLogger("Try AudioRecord rate " + rate + "Hz, bits: " + audioFormat + ", channelInConfig: " + channelInConfig, false);
                        }
                        int buffSize = AudioRecord.getMinBufferSize(rate, channelInConfig, audioFormat);
                        // force buffSize to powersOfTwo if it isnt (ie.S5)
                        buffSize = getClosestPowersHigh(buffSize);

                        if (buffSize != AudioRecord.ERROR_BAD_VALUE) {
                            AudioRecord recorder = new AudioRecord(
                                    audioSource,
                                    rate,
                                    channelInConfig,
                                    audioFormat,
                                    buffSize);

                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                                // AudioRecord.getChannelCount() is number of input audio channels (1 is mono, 2 is stereo)
                                if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15])) {
                                    debugLogger("AudioRecord found: " + rate + ", buffer: " + buffSize + ", channel count: " + recorder.getChannelCount(), true);
                                }
                                // set found values
                                channelInCount = recorder.getChannelCount();
                                audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[0], audioSource);
                                audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[1], rate);
                                audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[2], channelInConfig);
                                audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[3], audioFormat);
                                audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[4], buffSize);

                                recorder.release();
                                return true;
                            }
                        }
                    }
                    catch (Exception e) {
                        if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15])) {
                            debugLogger("Error, keep trying.", false);
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean determineOutputAudioType() {
        // guaranteed default for Android is 44.1kHz, PCM_16BIT, CHANNEL_IN_DEFAULT
        for (int rate : AudioSettings.SAMPLE_RATES) {
            for (short audioFormat : new short[] {
                    AudioFormat.ENCODING_PCM_16BIT,
                    AudioFormat.ENCODING_PCM_8BIT}) {

                for (short channelOutConfig : new short[] {
                        AudioFormat.CHANNEL_OUT_DEFAULT, // 1 - switched by OS, not native?
                        AudioFormat.CHANNEL_OUT_MONO,    // 4
                        AudioFormat.CHANNEL_OUT_STEREO }) {  // 12
                    try {
                        if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15])) {
                            debugLogger("Try Output rate " + rate + "Hz, bits: " + audioFormat + ", channelOutConfig: " + channelOutConfig, false);
                        }

                        int buffSize = AudioTrack.getMinBufferSize(rate, channelOutConfig, audioFormat);
                        if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15])) {
                            debugLogger("reported minBufferSize: " + buffSize, false);
                        }
                        // AudioTrack at create wants bufferSizeInBytes, the total size (in bytes)

                        AudioTrack audioTrack = new AudioTrack(
                                AudioManager.STREAM_MUSIC,
                                rate,
                                channelOutConfig,
                                audioFormat,
                                buffSize,
                                AudioTrack.MODE_STREAM);


                        if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                            if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15])) {
                                debugLogger("Output found: " + rate + ", buffer: " + buffSize + ", channelOutConfig: " + channelOutConfig, true);
                            }
                            // set output values
                            // buffOutSize may not be same as buffInSize conformed to powersOfTwo
                            audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[5], channelOutConfig);
                            audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[6], buffSize);
                            audioBundle.putInt(AudioSettings.AUDIO_BUNDLE_KEYS[13], (int)(rate * 0.5f));

                            // test onboardEQ
                            if (testOnboardEQ(audioTrack.getAudioSessionId())) {
                                if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15])) {
                                    debugLogger(context.getString(R.string.eq_check_2) + "\n", false);
                                }
                                audioBundle.putBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[12], true);
                            }
                            else {
                                if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15])) {
                                    debugLogger(context.getString(R.string.eq_check_3) + "\n", true);
                                }
                                audioBundle.putBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[12], false);
                            }
                            audioTrack.pause();
                            audioTrack.flush();
                            audioTrack.release();

                            if (buffSize > AudioSettings.POWERS_TWO_HIGH[4]) {
                                // stop Active Jammer from ever running if this?
                                // caution for potential laggy or breaking audiotrack buffer size of 8192
                                if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15]))
                                    debugLogger("Output buffer on this device may break active jammer.", true);
                            }

                            return true;
                        }
                    }
                    catch (Exception e) {
                        if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15]))
                            debugLogger("Error, keep trying.", false);
                    }
                }
            }
        }
        return false;
    }

    // testing android/media/audiofx/Equalizer
    // idea is to make the whitenoise less annoying
    // vers 4.0.6 - rem'ing the EQ changes, is now merely a report function
    private boolean testOnboardEQ(int audioSessionId) {
        try {
            Equalizer equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(true);
            // get some info
            short bands = equalizer.getNumberOfBands();
            final short minEQ = equalizer.getBandLevelRange()[0]; // returns milliBel
            final short maxEQ = equalizer.getBandLevelRange()[1];

            if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15])) {
                debugLogger("\n" + context.getString(R.string.eq_check_1), false);
                debugLogger(context.getString(R.string.eq_check_4) + bands, false);
                debugLogger(context.getString(R.string.eq_check_5) + minEQ, false);
                debugLogger(context.getString(R.string.eq_check_6) + maxEQ, false);
            }

            if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15])) {
                for (short band = 0; band < bands; band++) {
                    // divide by 1000 to get numbers into recognisable ranges
                    debugLogger("\nband freq range min: " + (equalizer.getBandFreqRange(band)[0] / 1000), false);
                    debugLogger("Band " + band + " center freq Hz: " + (equalizer.getCenterFreq(band) / 1000), true);
                    debugLogger("band freq range max: " + (equalizer.getBandFreqRange(band)[1] / 1000), false);
                    // band 5 reports center freq: 14kHz, minrange: 7000 and maxrange: 0  <- is this infinity? uppermost limit?
                    // could be 21kHz if report standard of same min to max applies.
                }
            }

            // vers 4.0.6 - Equalizer appears to be NOT application specific, and is applied across device:
            // Removing the EQ changes as the Active jammer is currently not optimal.

            // only active test is to squash all freqs in bands 0-3, leaving last band (4) free...
            /*
            if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[15])) {
                debugLogger("\n" + context.getString(R.string.eq_check_7) + minEQ, false);
            }

            for (int i = 0; i < 2; i++) {
                for (short j = 0; j < bands; j++) {
                    equalizer.setBandLevel(j, minEQ);
                }
            }
            */
            // not a filter... reduced amplitude seems the best description when using eq.
            // repeat calls to -15 dB improves sound reduction
            // band4 to maxEQ will prob not do anything useful?

            return true;
        }
        catch (Exception ex) {
            debugLogger(context.getString(R.string.eq_check_8), true);
            ex.printStackTrace();
            return false;
        }
    }

    public boolean checkAudioRecord() {
        // return if can start new audioRecord object
        AudioRecord audioRecord;
        try {
            audioRecord = new AudioRecord(
                    audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[0]),
                    audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[1]),
                    audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[2]),
                    audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[3]),
                    audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[4]));
            MainActivity.logger(context.getString(R.string.audio_check_4));
            // need to start reading buffer to trigger an exception
            audioRecord.startRecording();
            short buffer[] = new short[audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[4])];
            int audioStatus = audioRecord.read(buffer, 0, audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[4]));

            // check for error on pre 6.x and 6.x API
            if(audioStatus == AudioRecord.ERROR_INVALID_OPERATION
                    || audioStatus == AudioRecord.STATE_UNINITIALIZED) {
                MainActivity.logger(context.getString(R.string.audio_check_6) + audioStatus);
                // audioStatus == 0(uninitialized) is an error, does not throw exception
                MainActivity.logger(context.getString(R.string.audio_check_5));
                return false;
            }
        }
        catch(Exception e) {
            MainActivity.logger(context.getString(R.string.audio_check_7));
            MainActivity.logger(context.getString(R.string.audio_check_9));
            return false;
        }
        // no errors
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
        MainActivity.logger(context.getString(R.string.audio_check_8));
        return true;
    }

    /*********************/
    public String saveFormatToString() {
        return (audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[1]) + " Hz, "
                + audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[20]) + " bits, "
                + channelInCount + " channel");
    }


    private void debugLogger(String message, boolean caution) {
        // for the times that fragments arent attached etc, print to adb
        if (caution && DEBUG) {
            Log.e(TAG, message);
        }
        else if ((!caution) && DEBUG) {
            Log.d(TAG, message);
        }
        else {
            Log.i(TAG, message);
        }
    }
}
/*
                S5 returns:
                bands: 5
                minEQ: -1500 (-15 dB)
                maxEQ: 1500  (+15 dB)
                eqLevelRange: 2
                band 0
                    ctr: 60
                    min: 30
                    max: 120
                band 1
                    ctr: 230
                    min: 120
                    max: 460
                band 2
                    ctr: 910
                    min: 460
                    max: 1800
                band 3
                    ctr: 3600
                    min: 1800
                    max: 7000
                band 4
                    ctr: 14000
                    min: 7000
                    max: 0

notes: media/libeffects/lvm/lib/Eq/lib/LVEQNB.h
    /*      Gain        is in integer dB, range -15dB to +15dB inclusive                    */
/*      Frequency   is the centre frequency in Hz, range DC to Nyquist                  */
/*      QFactor     is the Q multiplied by 100, range 0.25 (25) to 12 (1200)            */
/*                                                                                      */
/*  Example:                                                                            */
/*      Gain = 7            7dB gain                                                    */
/*      Frequency = 2467    Centre frequency = 2.467kHz                                 */
    /*      QFactor = 1089      Q = 10.89

    // --> THERE'S A Q ?

*/

