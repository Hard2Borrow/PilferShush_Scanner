package cityfreqs.com.pilfershush.jammers;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.Equalizer;
import android.os.Bundle;

import java.util.Random;

import cityfreqs.com.pilfershush.MainActivity;
import cityfreqs.com.pilfershush.R;
import cityfreqs.com.pilfershush.assist.AudioSettings;

public class ActiveJammer {
    private Context context;
    private Bundle audioBundle;
    private float amplitude;
    private AudioTrack audioTrack;
    private boolean isPlaying;
    private Thread jammerThread;

    public ActiveJammer(Context context, Bundle audioBundle) {
        this.context = context;
        this.audioBundle = audioBundle;
        resetActiveJammer();
    }

    private void resetActiveJammer() {
        amplitude = 1.0f;
        audioTrack = null;
        isPlaying = false;

        // device unique, override reported maximum frequency?
        // ie. if sampleRate found is 22kHz, try run over it anyway
        //maximumDeviceFrequency = audioSettings.getSampleRate() * 0.5;
        //maximumDeviceFrequencyOverride = false;
    }

    /*
        PUBLIC CONTROLS
     */
    void play(int type) {
        // FFT to find key NUHF freq in the environment and tune jammer to it?
        if (isPlaying) {
            return;
        }
        //stop();
        isPlaying = true;
        threadPlay(type);
    }

    void stop() {
        isPlaying = false;
        if (audioTrack == null) {
            return;
        }
        stopPlayer();
    }
    /*
        AUDIO PLAY FUNCTIONS
     */
    private synchronized void threadPlay(int typeIn) {
        final int type = typeIn;
        jammerThread = new Thread() {
            public void run() {
                try {
                    audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                            audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[1]),
                            audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[5]),
                            audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[3]),
                            audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[6]),
                            AudioTrack.MODE_STREAM);

                    audioTrack.setStereoVolume(amplitude, amplitude);

                    if (audioBundle.getBoolean(AudioSettings.AUDIO_BUNDLE_KEYS[12])) {
                        onboardEQ(audioTrack.getAudioSessionId());
                    }

                    while (isPlaying) {
                        if (type == AudioSettings.JAMMER_TONE) {
                            createTone();
                        }
                        if (type == AudioSettings.JAMMER_WHITE) {
                            createWhiteNoise();
                        }
                    }
                }
                catch (Exception ex) {
                    MainActivity.entryLogger(context.getResources().getString(R.string.active_state_1), true);
                }
            }
        };
        jammerThread.start();
    }

    private void stopPlayer() {
        isPlaying = false;
        if (jammerThread != null) {
            try {
                jammerThread.interrupt();
                jammerThread.join();
                jammerThread = null;
            }
            catch (Exception ex) {
                MainActivity.entryLogger(context.getResources().getString(R.string.active_state_2), true);
            }
        }
        try {
            audioTrack.pause();
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
        }
        catch (IllegalStateException e) {
            MainActivity.entryLogger(context.getResources().getString(R.string.active_state_3), true);
        }
    }

    private synchronized int loadDriftTone() {
        switch (audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[8])) {
            case AudioSettings.JAMMER_TYPE_TEST:
                return getTestDrift();

            case AudioSettings.JAMMER_TYPE_NUHF:
                return getNuhfDrift(audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[13]));

            case AudioSettings.JAMMER_TYPE_DEFAULT_RANGED:
                return getDefaultRangedDrift(audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[9]));

            case AudioSettings.JAMMER_TYPE_USER_RANGED:
                return getUserRangedDrift(
                        audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[9]),
                        audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[10]));

            default:
                return getTestDrift();
        }
    }

    private synchronized void createTone() {
        int sampleRate = audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[1]);
        int driftSpeed = audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[11]) * AudioSettings.DRIFT_SPEED_MULTIPLIER; // get into ms ranges
        double sample[] = new double[sampleRate];
        byte soundData[] = new byte[2 * sampleRate];

        // NOTES: remove clicks from android audio emit, waveform at pop indicates no zero crossings either side
        // - AMPLITUDE RAMPS pre and post every loadDriftTone() etc - not practical
        // - ZERO VALUE SAMPLES either side of loadDriftTone()
        // - can still be useful jamming sound ;)

        /*

        int ramp = audioSettings.getSampleRate() / 20;
        for (int i = 0; i < sampleRate; i++) [
            if (jammerTypeSwitch != AudioSettings.JAMMER_TYPE_TEST && i % driftSpeed == 0) {...}

            if (i < ramp) sample[i] = 0;
            else if (i > sampleRate - ramp) sample[i] = 0;
            else {
                // normal loop
            }
        }

        */

        int driftFreq = loadDriftTone();
        // every nth iteration get a new drift freq (48k rate / driftSpeed )
        for (int i = 0; i < sampleRate; ++i) {
            if (audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[8]) != AudioSettings.JAMMER_TYPE_TEST && i % driftSpeed == 0) {
                driftFreq = loadDriftTone();
            }
            // ramp/zero-crossing check could go here
            sample[i] = Math.sin(
                    driftFreq * 2 * Math.PI * i / (sampleRate));
        }

        int idx = 0;
        for (final double dVal : sample) {
            final short val = (short) ((dVal * 32767)); // max the amplitude
            // in 16 bit wav PCM, first byte is the low order byte
            soundData[idx++] = (byte) (val & 0x00ff);
            soundData[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        playSound(soundData);
    }

    private synchronized void createWhiteNoise() {
        byte soundData[] = new byte[audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[1])];
        new Random().nextBytes(soundData);

        for (int i = 0; i < soundData.length; i++) {
            soundData[i] *= amplitude;
        }
        playSound(soundData);
    }

    private synchronized void playSound(byte[] soundData) {
        if (audioBundle == null) {
            MainActivity.entryLogger(context.getResources().getString(R.string.audio_check_3), true);
            return;
        }

        try {
            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                audioTrack.play();
                audioTrack.write(soundData, 0, soundData.length);
            }
            else {
                MainActivity.entryLogger(context.getResources().getString(R.string.audio_check_3), true);
            }
        }
        catch (Exception e) {
            MainActivity.entryLogger(context.getResources().getString(R.string.audio_check_4), true);
        }
    }


    // this works reasonably well for the tone, but not whitenoise.
    private void onboardEQ(int audioSessionId) {
        try {
            Equalizer equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(true);
            short bands = equalizer.getNumberOfBands();
            final short minEQ = equalizer.getBandLevelRange()[0];
            final short maxEQ = equalizer.getBandLevelRange()[1];

            // attempt a HPF, to reduce (~15dB) all freqs in bands 0-3, boost band 4
            for (int i = 0; i < 2; i++) {
                for (short j = 0; j < bands; j++) {
                    equalizer.setBandLevel(j, minEQ);
                }
                // boost band 4 twice
                equalizer.setBandLevel((short)4, maxEQ);
            }
        }
        catch (Exception ex) {
            MainActivity.entryLogger("onboardEQ Exception.", true);
            ex.printStackTrace();
        }
    }

    private int getTestDrift() {
        return new Random().nextInt(AudioSettings.MAXIMUM_TEST_FREQUENCY
                - AudioSettings.MINIMUM_TEST_FREQUENCY)
                + AudioSettings.MINIMUM_TEST_FREQUENCY;
    }

    private int getNuhfDrift(int maxFreq) {
        return new Random().nextInt(maxFreq
                - AudioSettings.MINIMUM_NUHF_FREQUENCY)
                + AudioSettings.MINIMUM_NUHF_FREQUENCY;
    }

    private int getDefaultRangedDrift(int carrierFrequency) {
        int min = conformMinimumRangedValue(carrierFrequency - AudioSettings.DEFAULT_RANGE_DRIFT_LIMIT);
        int max = conformMaximumRangedValue(carrierFrequency + AudioSettings.DEFAULT_RANGE_DRIFT_LIMIT);

        return new Random().nextInt(max - min) + min;
    }

    // carrier should be between 18k - 24k
    private int getUserRangedDrift(int carrierFrequency, int limit) {
        carrierFrequency = conformCarrierFrequency(carrierFrequency);
        int min = conformMinimumRangedValue(carrierFrequency - limit);
        int max = conformMaximumRangedValue(carrierFrequency + limit);

        return new Random().nextInt(max - min) + min;
    }

    private int conformCarrierFrequency(int carrier) {
        if (carrier < AudioSettings.MINIMUM_NUHF_FREQUENCY)
            carrier = AudioSettings.MINIMUM_NUHF_FREQUENCY;
        if (carrier > audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[13]))
            carrier = audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[13]);
        return carrier;
    }

    private int conformMinimumRangedValue(int minValue) {
        if (minValue >= AudioSettings.MINIMUM_NUHF_FREQUENCY)
            return minValue;
        else
            return AudioSettings.MINIMUM_NUHF_FREQUENCY;
    }

    private int conformMaximumRangedValue(int maxValue) {
        if (maxValue <= audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[13]))
            return maxValue;
        else
            return audioBundle.getInt(AudioSettings.AUDIO_BUNDLE_KEYS[13]);
    }
}
