package com.ziqi.codesim.preprocess;

import org.junit.jupiter.api.Test;
import java.util.List;
import com.ziqi.codesim.preprocess.Lang;

import static org.junit.jupiter.api.Assertions.*;

public class TokenizerTest {

    @Test
    void shouldNotTreatCommentInsideStringAsComment() {
        String code = "char* s = \"http://a.com//path\"; // real comment\nreturn 0;";

        Lang.Spec spec = Lang.detect("demo.c"); // 用后缀猜语言即可
        List<String> t = Tokenizer.tokenizeForBaseline(code, spec);

        // 1) 字符串里出现 // 不应该触发注释删除
        // 2) 行注释后面的 return 仍然应该被 token 出来
        assertTrue(t.contains("STR"));
        assertTrue(t.contains("return"));
        assertTrue(t.contains("NUM"));
    }

    @Test
    void baselineShouldCollapseIdentifiersAndNumbers() {
        String code = "int abc = 123; int def = abc + 7;";

        Lang.Spec spec = Lang.detectLanguage("demo.c");
        List<String> t = Tokenizer.tokenizeForBaseline(code, spec);

        // identifiers collapse to ID, numbers collapse to NUM
        assertTrue(t.contains("ID"));
        assertTrue(t.contains("NUM"));

        // 旧的具体名字不应该出现（因为会被折叠成 ID/NUM）
        assertFalse(t.contains("abc"));
        assertFalse(t.contains("123"));
    }

    @Test
    void shouldHandleBlockComments() {
        String code = "int a=1; /* comment */ int b=2;";

        Lang.Spec spec = Lang.detectLanguage("demo.c");
        List<String> t = Tokenizer.tokenizeForBaseline(code, spec);

        // 注释应被跳过，但前后代码 tokens 还在
        assertTrue(t.contains("int"));
        assertTrue(t.contains("NUM"));
    }
}
