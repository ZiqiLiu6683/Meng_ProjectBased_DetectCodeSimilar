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
    public void removePainel(Channel c) {
        Component[] components = painelCentral.getComponents();
        for (Component comp : components) {
            if (comp instanceof PainelCanal) {
                if (((PainelCanal) comp).getCanal() == c) {
                    painelCentral.remove(comp);
                    abas.remove(comp);
                    Client.getInstance().getChannels().removerCanal(c);
                }
            }
        }
    }
}
