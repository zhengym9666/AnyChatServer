/**
 * @ClassName:
 * @Description: TODO
 * @author linyb3
 * @date
 */

import net.sf.json.JSONObject;
import org.anychat.config.CommonConfigChat;
import org.anychat.log.HttpclientLog;
import org.grain.httpclient.HttpUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName:
 * @Description: TODO
 * @author linyb3
 * @date
 *
 */
public class TestDateSend {
    public static void main(String[] args) throws Exception {
        HttpUtil.init("UTF-8", new HttpclientLog());
        JSONObject js = new JSONObject();
        js.put("hOpCode", "13");
        js.put("userGroupTopId", "123");
        Map<String, String> header = new HashMap<>();
        header.put("test", "13");
        header.put("token", "123123");

        byte[] returnByte = HttpUtil.send(js.toString(), "http://localhost:8080/ori/test/test.action", header, HttpUtil.POST);
        System.out.println(returnByte.length);
//        byte[] result = HttpUtil.send(null, "http://www.baidu.com", null, HttpUtil.GET);
//        System.out.println(result);
    }
}
