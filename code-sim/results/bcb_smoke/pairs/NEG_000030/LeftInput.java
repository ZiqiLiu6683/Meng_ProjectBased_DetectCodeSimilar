import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    public void onMessage(EndGameMessage m, List<Message> out) {
        out.add(m);
        if (getChannel().getGameState() != STOPPED) {
            stopWatch.stop();
            displayStats(out);
        }
    }
}
