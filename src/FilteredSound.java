import java.io.*;
import javax.sound.sampled.*;

/**
 * FilteredSound plays a .wav file but applies a custom byte-level mathematical
 * filter to the audio data before sending it to the speakers.
 * This fulfils the "novel sound filter" requirement.
 */
public class FilteredSound extends Thread {

    String filename; // The name of the file to play
    boolean finished; // A flag showing that the thread has finished

    public FilteredSound(String fname) {
        filename = fname;
        finished = false;
    }

    /**
     * Applies a custom 'Echo/Delay' filter to a raw byte array of audio data.
     * It assumes 16-bit, signed, little-endian PCM data (standard for simple .wavs).
     */
    private void applyEchoFilter(byte[] audioBytes, AudioFormat format) {
        int bytesPerSample = format.getSampleSizeInBits() / 8;
        if (bytesPerSample != 2) return; // Only process 16-bit audio
        
        int channels = format.getChannels();
        
        // Echo delay in samples (e.g. 100ms delay)
        int delayInSamples = (int)(format.getSampleRate() * 0.10) * channels;
        int delayInBytes = delayInSamples * bytesPerSample;
        
        // We need an array to hold the original audio so we can mix it with a delay
        byte[] original = new byte[audioBytes.length];
        System.arraycopy(audioBytes, 0, original, 0, audioBytes.length);

        for (int i = delayInBytes; i < audioBytes.length - 1; i += 2) {
            // Reconstruct the 16-bit little-endian sample for the current time
            short currentSample = (short) ((audioBytes[i+1] << 8) | (audioBytes[i] & 0xFF));
            
            // Reconstruct the 16-bit little-endian sample from the past (the echo)
            int pastIndex = i - delayInBytes;
            short pastSample = (short) ((original[pastIndex+1] << 8) | (original[pastIndex] & 0xFF));
            
            // Mix them together (current + 50% volume of past)
            int mixed = currentSample + (pastSample / 2);
            
            // Clamp the value to prevent clipping/distortion
            if (mixed > Short.MAX_VALUE) mixed = Short.MAX_VALUE;
            if (mixed < Short.MIN_VALUE) mixed = Short.MIN_VALUE;
            
            // Put the mixed 16-bit sample back into the byte array
            audioBytes[i] = (byte) (mixed & 0xFF);
            audioBytes[i+1] = (byte) ((mixed >> 8) & 0xFF);
        }
    }

    public void run() {
        try {
            File file = new File(filename);
            AudioInputStream stream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = stream.getFormat();
            
            // Read all bytes from the stream
            int frameLength = (int) stream.getFrameLength();
            int frameSize = format.getFrameSize();
            byte[] bytes = new byte[frameLength * frameSize];
            int bytesRead = stream.read(bytes);

            // ── APPLY OUR NOVEL FILTER HERE ──
            if (bytesRead > 0) {
                applyEchoFilter(bytes, format);
            }

            // Create a new stream from the filtered bytes
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            AudioInputStream filteredStream = new AudioInputStream(bais, format, frameLength);

            // Play the filtered stream
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            Clip clip = (Clip) AudioSystem.getLine(info);
            clip.open(filteredStream);
            clip.start();
            Thread.sleep(100);
            while (clip.isRunning()) {
                Thread.sleep(100);
            }
            clip.close();
        } catch (Exception e) {
            System.out.println("Error playing filtered sound: " + e.getMessage());
        }
        finished = true;
    }
}
