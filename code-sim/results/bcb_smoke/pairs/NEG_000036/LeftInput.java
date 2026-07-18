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
    @Test
    public void testAvarageScore() {
        int a = 10;
        int b = 6;
        double avg = (a + b) / 2;
        Evaluation evalOne = createStrictMock(Evaluation.class);
        Evaluation evalTwo = createStrictMock(Evaluation.class);
        expect(evalOne.getScore()).andReturn(a);
        expect(evalTwo.getScore()).andReturn(b);
        replay(evalOne);
        replay(evalTwo);
        tasterBeanUnderTest.getEvaluations().add(evalOne);
        tasterBeanUnderTest.getEvaluations().add(evalTwo);
        assertEquals(avg, tasterBeanUnderTest.avarageScore());
    }
}
