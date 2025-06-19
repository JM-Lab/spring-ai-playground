package jm.kr.spring.ai.playground;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

@SpringBootTest
class SpringAiPlaygroundApplicationTests {

    @Test
    void contextLoads() {
        new ArrayList<String>(Arrays.asList()).stream().collect(Collectors.joining("\n"));
    }

}
