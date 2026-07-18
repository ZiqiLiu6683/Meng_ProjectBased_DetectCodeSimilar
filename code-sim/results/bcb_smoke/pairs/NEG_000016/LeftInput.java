import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.*;
import java.math.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class LeftInput {
    public String toString() {
        return getBoardIdentifier() + "-" + getCommChannel().getChannelName() + "(" + this.getAddress() + ")";
    }
}
