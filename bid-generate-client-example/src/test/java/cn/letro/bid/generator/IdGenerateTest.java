package cn.letro.bid.generator;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.SystemClock;
import cn.letro.bid.generator.client.IdGeneratorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.junit.Test;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认标题
 * 默认描述
 *
 * @author Letro Liu
 * @date 2021-11-02
 */
public class IdGenerateTest extends IdGenerateApplicationTest {

    @Resource
    private IdGeneratorService generator;

    @Test
    public void genNoGetOneTest() {
        String no = generator.genBizNoByDay("TEST", 3, false);
        System.out.println("====单号：" + no);
        no = generator.genBizNoByDay("TEST", 3, true);
        System.out.println("====单号：" + no);
        no = generator.genBizNoByImmortal("TEST2", 3, true);
        System.out.println("====单号：" + no);
    }

    @Test
    public void genNoTest() {
        genNo();
    }

    private String genNo() {
        ObjectMapper objectMapper = new ObjectMapper();
        int core2 = Runtime.getRuntime().availableProcessors() * 2;
        final int count = (((int)(Math.random() * 10)) & 1) == 1 ? 20 : core2;
        final int loop = 2000;
        CountDownLatch countDownLatch = new CountDownLatch(count);
        CountDownLatch countDownLatch2 = new CountDownLatch(count);
        AtomicLong duration = new AtomicLong(0);
        AtomicInteger okTotal = new AtomicInteger(0);
        long begin = SystemClock.now();
        Runnable runnable = () -> {
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            List<String> success = Lists.newArrayList();
            List<String> fails = Lists.newArrayList();
            long beginTime = SystemClock.now();
            for (int i = 0; i < loop; i++) {
                try {
                    String no = generator.genBizNoByDay("TEST", 3);
                    success.add(no);
                } catch (Exception e) {
                    fails.add(e.getMessage());
                }
            }
            //"获取单号异常" "获取单号成功"
            long dTime = (SystemClock.now() - beginTime);
            duration.addAndGet(dTime);
            okTotal.addAndGet(success.size());
            try {
                System.out.println(String.format("耗时：%sms|成功:%s|失败:%s", dTime, objectMapper.writeValueAsString(success), objectMapper.writeValueAsString(fails)));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            countDownLatch2.countDown();
        };
        for (int i = 0; i < count; i++) {
            Thread thread = new Thread(runnable);
            thread.start();
            countDownLatch.countDown();
        }
        try {
            countDownLatch2.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        String logStr = "%s|执行耗时:%sms,取号总耗时:%sms,总计:%s个,成功:%s个,失败:%s个,平均%sms/个,线程:%s个,循环:%s次";
        String result = String.format(logStr,
                DateUtil.formatDateTime(new Date()),
                (SystemClock.now() - begin),
                duration.get(),
                (count * loop),
                okTotal.get(),
                (count * loop) - okTotal.get(),
                duration.get() / (count * loop),
                count,
                loop);
        System.out.println(result);
        return result;
        //测试结果均基于突发流量，受测试环境服务稳定性影响
        //压测结果1：5线程各取200号共1000号，耗时2012ms+1982ms+1982ms+1810ms+1623ms=9409ms，失败0
        //压测结果2：5线程各取200号共1000号，耗时1303ms+1365ms+1982ms+1396ms+1427ms=7473ms，失败0
        //压测结果3：10线程各取1000号共10000号，耗时3673ms，0失败
        //压测结果4：10线程各取1000号共10000号，耗时34134ms，453失败
        //压测结果5：10线程各取1000号共10000号，耗时4253ms，6失败
        //压测结果6：10线程各取1000号共10000号，耗时4692ms，0失败
        //压测结果7：10线程各取1000号共10000号，耗时4028ms，0失败
        //压测结果8：10线程各取1000号共10000号，耗时6163ms，0失败
        //压测结果9：10线程各取1000号共10000号，耗时4761ms，0失败
        //压测结果10：10线程各取1000号共10000号，耗时4473ms，0失败
    }

    @Test
    public void loopTest() {
        final String newLine = System.getProperty("line.separator");
        final int loop = 20;
        StringBuilder sb = new StringBuilder(loop * 2);
        for (int i = 0; i < loop; i++) {
            sb.append(genNo()).append(newLine);
        }
        System.out.println("=====================================================");
        System.out.println(sb.toString());
        /*
        初始库是从第一个值开始取号的，所以在模拟从弱流量到突发流量的适应能力，因为步长是基于前一段时间的取好量统计的结果，
        而非初始库可能是基于一个较大的缓存初始值，故遇到突发流量时应对相比初始库更自如
        非初始库
        2021-11-03 15:11:39|执行耗时:7916ms,取号总耗时:124872ms,总计:32000个,成功:32000个,失败:0个,平均3ms/个,线程:16个,循环:2000次
        2021-11-03 15:11:45|执行耗时:5585ms,取号总耗时:88816ms,总计:32000个,成功:32000个,失败:0个,平均2ms/个,线程:16个,循环:2000次
        2021-11-03 15:11:52|执行耗时:6604ms,取号总耗时:130739ms,总计:40000个,成功:40000个,失败:0个,平均3ms/个,线程:20个,循环:2000次
        2021-11-03 15:11:58|执行耗时:6522ms,取号总耗时:103746ms,总计:32000个,成功:32000个,失败:0个,平均3ms/个,线程:16个,循环:2000次
        2021-11-03 15:12:03|执行耗时:5332ms,取号总耗时:84955ms,总计:32000个,成功:32000个,失败:0个,平均2ms/个,线程:16个,循环:2000次
        2021-11-03 15:12:09|执行耗时:5682ms,取号总耗时:112008ms,总计:40000个,成功:40000个,失败:0个,平均2ms/个,线程:20个,循环:2000次
        2021-11-03 15:12:15|执行耗时:5462ms,取号总耗时:108298ms,总计:40000个,成功:40000个,失败:0个,平均2ms/个,线程:20个,循环:2000次
        2021-11-03 15:12:20|执行耗时:5653ms,取号总耗时:111708ms,总计:40000个,成功:40000个,失败:0个,平均2ms/个,线程:20个,循环:2000次
        2021-11-03 15:12:25|执行耗时:4844ms,取号总耗时:77104ms,总计:32000个,成功:32000个,失败:0个,平均2ms/个,线程:16个,循环:2000次
        2021-11-03 15:12:31|执行耗时:5575ms,取号总耗时:110560ms,总计:40000个,成功:40000个,失败:0个,平均2ms/个,线程:20个,循环:2000次
        2021-11-03 15:12:36|执行耗时:5536ms,取号总耗时:109690ms,总计:40000个,成功:40000个,失败:0个,平均2ms/个,线程:20个,循环:2000次
        2021-11-03 15:12:42|执行耗时:5414ms,取号总耗时:107485ms,总计:40000个,成功:40000个,失败:0个,平均2ms/个,线程:20个,循环:2000次
        2021-11-03 15:12:47|执行耗时:5536ms,取号总耗时:109671ms,总计:40000个,成功:40000个,失败:0个,平均2ms/个,线程:20个,循环:2000次
        2021-11-03 15:12:53|执行耗时:5714ms,取号总耗时:90782ms,总计:32000个,成功:32000个,失败:0个,平均2ms/个,线程:16个,循环:2000次
        2021-11-03 15:12:59|执行耗时:5773ms,取号总耗时:114740ms,总计:40000个,成功:40000个,失败:0个,平均2ms/个,线程:20个,循环:2000次
        2021-11-03 15:13:04|执行耗时:5153ms,取号总耗时:82060ms,总计:32000个,成功:32000个,失败:0个,平均2ms/个,线程:16个,循环:2000次
        2021-11-03 15:13:09|执行耗时:5349ms,取号总耗时:84785ms,总计:32000个,成功:32000个,失败:0个,平均2ms/个,线程:16个,循环:2000次
        2021-11-03 15:13:18|执行耗时:9053ms,取号总耗时:179541ms,总计:40000个,成功:40000个,失败:0个,平均4ms/个,线程:20个,循环:2000次
        2021-11-03 15:13:24|执行耗时:5640ms,取号总耗时:111545ms,总计:40000个,成功:40000个,失败:0个,平均2ms/个,线程:20个,循环:2000次
        2021-11-03 15:13:29|执行耗时:4981ms,取号总耗时:78964ms,总计:32000个,成功:32000个,失败:0个,平均2ms/个,线程:16个,循环:2000次
        初始库
        2021-11-03 15:17:29|执行耗时:15085ms,取号总耗时:292578ms,总计:40000个,成功:39989个,失败:11个,平均7ms/个,线程:20个,循环:2000次
        2021-11-03 15:17:37|执行耗时:8057ms,取号总耗时:128640ms,总计:32000个,成功:32000个,失败:0个,平均4ms/个,线程:16个,循环:2000次
        2021-11-03 15:17:45|执行耗时:8467ms,取号总耗时:168365ms,总计:40000个,成功:40000个,失败:0个,平均4ms/个,线程:20个,循环:2000次
        2021-11-03 15:17:55|执行耗时:9805ms,取号总耗时:154445ms,总计:32000个,成功:32000个,失败:0个,平均4ms/个,线程:16个,循环:2000次
        2021-11-03 15:18:02|执行耗时:6889ms,取号总耗时:109294ms,总计:32000个,成功:32000个,失败:0个,平均3ms/个,线程:16个,循环:2000次
        2021-11-03 15:18:14|执行耗时:11759ms,取号总耗时:186987ms,总计:32000个,成功:32000个,失败:0个,平均5ms/个,线程:16个,循环:2000次
        2021-11-03 15:18:24|执行耗时:10523ms,取号总耗时:167194ms,总计:32000个,成功:32000个,失败:0个,平均5ms/个,线程:16个,循环:2000次
        2021-11-03 15:18:32|执行耗时:7441ms,取号总耗时:118559ms,总计:32000个,成功:32000个,失败:0个,平均3ms/个,线程:16个,循环:2000次
        2021-11-03 15:18:38|执行耗时:6551ms,取号总耗时:104170ms,总计:32000个,成功:32000个,失败:0个,平均3ms/个,线程:16个,循环:2000次
        2021-11-03 15:18:48|执行耗时:9731ms,取号总耗时:185571ms,总计:40000个,成功:40000个,失败:0个,平均4ms/个,线程:20个,循环:2000次
        2021-11-03 15:19:02|执行耗时:13578ms,取号总耗时:212632ms,总计:32000个,成功:32000个,失败:0个,平均6ms/个,线程:16个,循环:2000次
        2021-11-03 15:19:13|执行耗时:11406ms,取号总耗时:181865ms,总计:32000个,成功:32000个,失败:0个,平均5ms/个,线程:16个,循环:2000次
        2021-11-03 15:19:23|执行耗时:10177ms,取号总耗时:202028ms,总计:40000个,成功:40000个,失败:0个,平均5ms/个,线程:20个,循环:2000次
        2021-11-03 15:19:31|执行耗时:8354ms,取号总耗时:132649ms,总计:32000个,成功:32000个,失败:0个,平均4ms/个,线程:16个,循环:2000次
        2021-11-03 15:19:41|执行耗时:9770ms,取号总耗时:155669ms,总计:32000个,成功:32000个,失败:0个,平均4ms/个,线程:16个,循环:2000次
        2021-11-03 15:19:47|执行耗时:6018ms,取号总耗时:119588ms,总计:40000个,成功:40000个,失败:0个,平均2ms/个,线程:20个,循环:2000次
        2021-11-03 15:19:55|执行耗时:7647ms,取号总耗时:121755ms,总计:32000个,成功:32000个,失败:0个,平均3ms/个,线程:16个,循环:2000次
        2021-11-03 15:20:02|执行耗时:7467ms,取号总耗时:118874ms,总计:32000个,成功:32000个,失败:0个,平均3ms/个,线程:16个,循环:2000次
        2021-11-03 15:20:11|执行耗时:8162ms,取号总耗时:161708ms,总计:40000个,成功:40000个,失败:0个,平均4ms/个,线程:20个,循环:2000次
        2021-11-03 15:20:19|执行耗时:8878ms,取号总耗时:176339ms,总计:40000个,成功:40000个,失败:0个,平均4ms/个,线程:20个,循环:2000次
        */
    }
}
