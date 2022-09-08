package cn.letro.bid.generator.server;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-11-02
 */
@ActiveProfiles("local")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = BidGeneratorApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class IdGenerateApplicationTest {

    @Before
    public void testSetup() {
        initEnv();
    }

    private void initEnv() {
    }
}
