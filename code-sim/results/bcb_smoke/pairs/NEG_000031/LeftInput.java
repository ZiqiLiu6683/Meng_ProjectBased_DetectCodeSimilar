import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;
import java.util.zip.*;
import java.security.*;

public class LeftInput {
    public void setEvelope(Envelope e) {
        setBoundsInMicros(e.getTOn(), e.getTOff());
        gain = e.getGain();
        attackEnd = startByte + milliToByte(e.getTRise());
        decayStart = endByte - milliToByte(e.getTFall());
        if (attackEnd > decayStart) {
            long av = (attackEnd + decayStart) / 2;
            attackEnd = decayStart = (av / (nChannels * 2)) * nChannels * 2;
        }
    }
}
